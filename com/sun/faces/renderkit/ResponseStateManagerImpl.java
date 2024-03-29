/*
 * $Id: ResponseStateManagerImpl.java,v 1.52 2008/01/07 19:49:12 rlubke Exp $
 */

/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 *
 * Contributor(s):
 *
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */


package com.sun.faces.renderkit;

import com.sun.faces.config.WebConfiguration;
import com.sun.faces.config.WebConfiguration.BooleanWebContextInitParameter;
import com.sun.faces.config.WebConfiguration.WebContextInitParameter;
import com.sun.faces.config.WebConfiguration.WebEnvironmentEntry;
import com.sun.faces.io.Base64InputStream;
import com.sun.faces.io.Base64OutputStreamWriter;
import com.sun.faces.spi.SerializationProvider;
import com.sun.faces.spi.SerializationProviderFactory;
import com.sun.faces.util.Util;
import com.sun.faces.util.FacesLogger;
import com.sun.faces.util.RequestStateManager;

import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.faces.FacesException;
import javax.faces.application.StateManager;
import javax.faces.application.StateManager.SerializedView;
import javax.faces.context.FacesContext;
import javax.faces.context.ResponseWriter;
import javax.faces.render.RenderKitFactory;
import javax.faces.render.ResponseStateManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.BufferedOutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;


/**
 * <p>A <code>ResonseStateManager</code> implementation
 * for the default HTML render kit.
 */
public class ResponseStateManagerImpl extends ResponseStateManager {

    // Log instance for this class
    private static final Logger LOGGER = FacesLogger.RENDERKIT.getLogger();

    private static final char[] STATE_FIELD_START =
          ("<input type=\"hidden\" name=\""
           + ResponseStateManager.VIEW_STATE_PARAM
           + "\" id=\""
           + ResponseStateManager.VIEW_STATE_PARAM
           + "\" value=\"").toCharArray();

     private static final char[] STATE_FIELD_START_NO_ID =
          ("<input type=\"hidden\" name=\""
           + ResponseStateManager.VIEW_STATE_PARAM
           + "\" value=\"").toCharArray();

    private static final char[] STATE_FIELD_END =
          "\" />".toCharArray();

    private char[] stateFieldStart;
    private SerializationProvider serialProvider;
    private WebConfiguration webConfig;
    private Boolean compressState;
    private ByteArrayGuard guard;
    private int csBuffSize;

    public ResponseStateManagerImpl() {

        init();

    }

    /** @see {@link ResponseStateManager#getComponentStateToRestore(javax.faces.context.FacesContext)} */
    @Override
    @SuppressWarnings("deprecation")
    public Object getComponentStateToRestore(FacesContext context) {

        return RequestStateManager.get(context, RequestStateManager.FACES_VIEW_STATE);

    }



    /** @see {@link ResponseStateManager#isPostback(javax.faces.context.FacesContext)} */
    @Override
    public boolean isPostback(FacesContext context) {

        return context.getExternalContext().getRequestParameterMap().
              containsKey(ResponseStateManager.VIEW_STATE_PARAM);

    }


    /** @see {@link ResponseStateManager#getTreeStructureToRestore(javax.faces.context.FacesContext,String)} */
    @Override
    @SuppressWarnings("deprecation")
    public Object getTreeStructureToRestore(FacesContext context,
                                            String treeId) {

        Object s =
             RequestStateManager.get(context,
                                     RequestStateManager.FACES_VIEW_STRUCTURE);
        if (s != null) {
            return s;
        }

        StateManager stateManager = Util.getStateManager(context);

        String viewString = getStateParam(context);

        if (viewString == null) {
            return null;
        }

        if (stateManager.isSavingStateInClient(context)) {


            ObjectInputStream ois = null;

            try {
                InputStream bis;
                if (guard != null) {
                    bis = new CipherInputStream(
                          new Base64InputStream(viewString),
                          guard.getDecryptionCipher());
                } else {
                    bis = new Base64InputStream(viewString);
                }

                if (compressState) {
                    ois = serialProvider.createObjectInputStream(
                          new GZIPInputStream(bis));
                } else {
                    ois = serialProvider.createObjectInputStream(bis);
                }
                long stateTime = 0;
                if (webConfig.isSet(WebContextInitParameter.ClientStateTimeout)) {
                    try {
                        stateTime = ois.readLong();
                    } catch (IOException ioe) {
                        // we've caught an exception trying to read the time
                        // marker.  This most likely means a view that has been
                        // around before upgrading to the release that included
                        // this feature.  So, no marker, return null now to
                        // cause a ViewExpiredException
                        if (LOGGER.isLoggable(Level.FINE)) {
                            LOGGER.fine("Client state timeout is enabled, but unable to find the "
                                        + "time marker in the serialized state.  Assuming state "
                                        + "to be old and returning null.");
                        }
                        return null;
                    }
                }
                Object structure = ois.readObject();
                Object state = ois.readObject();
                if (stateTime != 0 && hasStateExpired(stateTime)) {
                    // return null if state has expired.  This should cause
                    // a ViewExpiredException to be thrown
                    return null;
                }
                storeStateInRequest(context, state);
                storeStructureInRequest(context, structure);
                return structure;

            } catch (java.io.OptionalDataException ode) {
                LOGGER.log(Level.SEVERE, ode.getMessage(), ode);
                throw new FacesException(ode);
            } catch (java.lang.ClassNotFoundException cnfe) {
                LOGGER.log(Level.SEVERE, cnfe.getMessage(), cnfe);
                throw new FacesException(cnfe);
            } catch (java.io.IOException iox) {
                LOGGER.log(Level.SEVERE, iox.getMessage(), iox);
                throw new FacesException(iox);
            } finally {
                if (ois != null) {
                    try {
                        ois.close();
                    } catch (IOException ioe) {
                        // ignore
                    }
                }
            }
        } else {
            return viewString;
        }
    }


    /** @see {@link ResponseStateManager#writeState(javax.faces.context.FacesContext,javax.faces.application.StateManager.SerializedView)} */
    @Override
    @SuppressWarnings("deprecation")
    public void writeState(FacesContext context, SerializedView view)
    throws IOException {

        StateManager stateManager = Util.getStateManager(context);
        ResponseWriter writer = context.getResponseWriter();

        writer.write(stateFieldStart);

        if (stateManager.isSavingStateInClient(context)) {
            ObjectOutputStream oos = null;
            try {

                Base64OutputStreamWriter bos =
                      new Base64OutputStreamWriter(csBuffSize,
                                                   writer);
                OutputStream base;
                if (guard != null) {
                    base = new CipherOutputStream(bos,
                                                  guard.getEncryptionCipher());
                } else {
                    base = bos;
                }
                if (compressState) {
                    oos = serialProvider.createObjectOutputStream(
                          new BufferedOutputStream(new GZIPOutputStream(base),
                                                   1024));
                } else {
                    oos = serialProvider
                          .createObjectOutputStream(new BufferedOutputStream(
                                base,
                                1024));
                }

                if (webConfig.isSet(WebContextInitParameter.ClientStateTimeout)) {
                    oos.writeLong(System.currentTimeMillis());
                }
                //noinspection NonSerializableObjectPassedToObjectStream
                oos.writeObject(view.getStructure());
                //noinspection NonSerializableObjectPassedToObjectStream
                oos.writeObject(view.getState());
                oos.flush();
                oos.close();

                // flush everything to the underlying writer
                bos.finish();

                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine("Client State: total number of characters"
                                + " written: " + bos.getTotalCharsWritten());
                }
            } finally {
                if (oos != null) {
                    try {
                        oos.close();
                    } catch (IOException ioe) {
                        // ignore
                    }
                }

            }

        } else {
            writer.write(view.getStructure().toString());
        }

        writer.write(STATE_FIELD_END);

        writeRenderKitIdField(context, writer);

    }


    /**
     * <p>If the {@link WebContextInitParameter#ClientStateTimeout} init parameter
     * is set, calculate the elapsed time between the time the client state was
     * written and the time this method was invoked during restore.  If the client
     * state has expired, return <code>true</code>.  If the client state hasn't expired,
     * or the init parameter wasn't set, return <code>false</code>.
     * @param stateTime the time in milliseconds that the state was written
     *  to the client
     * @return <code>false</code> if the client state hasn't timed out, otherwise
     *  return <code>true</code>
     */
    private boolean hasStateExpired(long stateTime) {

        if (webConfig.isSet(WebContextInitParameter.ClientStateTimeout)) {
            long elapsed = (System.currentTimeMillis() - stateTime) / 60000;
            int timeout = Integer.parseInt(
                  webConfig.getOptionValue(
                        WebContextInitParameter.ClientStateTimeout));

            return (elapsed > timeout);
        } else {
            return false;
        }

    }


    /**
     * <p>Store the state for this request into a temporary attribute
     * within the same request.</p>
     *
     * @param context the <code>FacesContext</code> of the current request
     * @param state   the view state
     */
    private static void storeStateInRequest(FacesContext context,
                                            Object state) {

        // store the state object temporarily in request scope
        // until it is processed by getComponentStateToRestore
        // which resets it.
        RequestStateManager.set(context,
                                RequestStateManager.FACES_VIEW_STATE,
                                state);

    }

    private static void storeStructureInRequest(FacesContext context,
                                                Object structure) {

        RequestStateManager.set(context,
                                RequestStateManager.FACES_VIEW_STRUCTURE,
                                structure);

    }


    /**
     * <p>Write a hidden field if the default render kit ID is not
     * RenderKitFactory.HTML_BASIC_RENDER_KIT.</p>
     *
     * @param context the <code>FacesContext</code> for the current request
     * @param writer  the target writer
     *
     * @throws IOException if an error occurs
     */
    private static void writeRenderKitIdField(FacesContext context,
                                              ResponseWriter writer)
          throws IOException {
        String result = context.getApplication().getDefaultRenderKitId();
        if (result != null &&
            !RenderKitFactory.HTML_BASIC_RENDER_KIT.equals(result)) {
            writer.startElement("input", context.getViewRoot());
            writer.writeAttribute("type", "hidden", "type");
            writer.writeAttribute("name",
                                  ResponseStateManager.RENDER_KIT_ID_PARAM,
                                  "name");
            writer.writeAttribute("value",
                                  result,
                                  "value");
            writer.endElement("input");
        }
    }

    /**
     * <p>Get our view state from this request</p>
     *
     * @param context the <code>FacesContext</code> for the current request
     *
     * @return the view state from this request
     */
    private static String getStateParam(FacesContext context) {

        String pValue = context.getExternalContext().getRequestParameterMap().
              get(ResponseStateManager.VIEW_STATE_PARAM);
        if (pValue != null && pValue.length() == 0) {
            pValue = null;
        }
        return pValue;

    }


    /**
     * <p>Perform the necessary intialization to make this
     * class work.</p>
     */
    private void init() {

        webConfig = WebConfiguration.getInstance();
        assert(webConfig != null);

        String pass = webConfig.getEnvironmentEntry(
                        WebEnvironmentEntry.ClientStateSavingPassword);
        if (pass != null) {
            guard = new ByteArrayGuard(pass);
        }
        compressState = webConfig.isOptionEnabled(
                            BooleanWebContextInitParameter.CompressViewState);
        String size = webConfig.getOptionValue(
                         WebContextInitParameter.ClientStateWriteBufferSize);
        String defaultSize =
              WebContextInitParameter.ClientStateWriteBufferSize.getDefaultValue();
        try {
            csBuffSize = Integer.parseInt(size);
            if (csBuffSize % 2 != 0) {
                if (LOGGER.isLoggable(Level.WARNING)) {
                    LOGGER.log(Level.WARNING,
                               "jsf.renderkit.resstatemgr.clientbuf_div_two",
                               new Object[] {
                                   WebContextInitParameter.ClientStateWriteBufferSize.getQualifiedName(),
                                   size,
                                   defaultSize});
                }
                csBuffSize = Integer.parseInt(defaultSize);
            } else {
                csBuffSize /= 2;
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine("Using client state buffer size of "
                                + csBuffSize);
                }
            }
        } catch (NumberFormatException nfe) {
            if (LOGGER.isLoggable(Level.WARNING)) {
                    LOGGER.log(Level.WARNING,
                               "jsf.renderkit.resstatemgr.clientbuf_not_integer",
                               new Object[] {
                                   WebContextInitParameter.ClientStateWriteBufferSize.getQualifiedName(),
                                   size,
                                   defaultSize});
                }
            csBuffSize = Integer.parseInt(defaultSize);
        }

        stateFieldStart = (webConfig
              .isOptionEnabled(BooleanWebContextInitParameter.EnableViewStateIdRendering)
                           ? STATE_FIELD_START
                           : STATE_FIELD_START_NO_ID);

        if (serialProvider == null) {
            serialProvider = SerializationProviderFactory
                  .createInstance(FacesContext.getCurrentInstance().getExternalContext());
        }
                
    }

   
} // end of class ResponseStateManagerImpl
