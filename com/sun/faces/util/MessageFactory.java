/*
 * $Id: MessageFactory.java,v 1.15 2007/04/27 22:02:51 ofung Exp $
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

package com.sun.faces.util;

import javax.el.ValueExpression;
import javax.faces.FactoryFinder;
import javax.faces.application.Application;
import javax.faces.application.ApplicationFactory;
import javax.faces.application.FacesMessage;
import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import javax.faces.el.ValueBinding;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * 
 * <p>supported filters: <code>package</code> and
 * <code>protection</code>.</p>
 */

public class MessageFactory {

    private MessageFactory() {
    }

    /**
     * @see #getMessage(String, Object...)
     * @param FacesMessage.Serverity set a custom severity
     */
    public static FacesMessage getMessage(String messageId,
                                                FacesMessage.Severity severity,
                                                Object... params) {
        FacesMessage message = getMessage(messageId, params);
        message.setSeverity(severity);
        return message;
    }


    /**
     * @see #getMessage(Locale, String, Object...)
     * @param FacesMessage.Serverity set a custom severity
     */
    public static FacesMessage getMessage(Locale locale,
                                                String messageId,
                                                FacesMessage.Severity severity,
                                                Object... params) {
        FacesMessage message = getMessage(locale, messageId, params);
        message.setSeverity(severity);
        return message;
    }


    /**
     * @see #getMessage(FacesContext, String, Object...)
     * @param FacesMessage.Serverity set a custom severity
     */
    public static FacesMessage getMessage(FacesContext context,
                                                String messageId,
                                                FacesMessage.Severity severity,
                                                Object... params) {
        FacesMessage message = getMessage(context, messageId, params);
        message.setSeverity(severity);
        return message;
    }

   
    /**
     * <p>This version of getMessage() is used for localizing implementation
     * specific messages.</p>
     *
     * @param messageId - the key of the message in the resource bundle
     * @param params    - substittion parameters
     *
     * @return a localized <code>FacesMessage</code> with the severity
     *  of FacesMessage.SEVERITY_ERROR
     */
     public static FacesMessage getMessage(String messageId, 
                                                 Object... params) {
        Locale locale = null;
        FacesContext context = FacesContext.getCurrentInstance();
        // context.getViewRoot() may not have been initialized at this point.
        if (context != null && context.getViewRoot() != null) {
            locale = context.getViewRoot().getLocale();
            if (locale == null) {
                locale = Locale.getDefault();
            }
        } else {
            locale = Locale.getDefault();
        }
        
        return getMessage(locale, messageId, params);
    }

     /**
      * <p>Creates and returns a FacesMessage for the specified Locale.</p>
      *
      * @param locale    - the target <code>Locale</code>
      * @param messageId - the key of the message in the resource bundle
      * @param params    - substittion parameters
      *
      * @return a localized <code>FacesMessage</code> with the severity
      *  of FacesMessage.SEVERITY_ERROR
      */
     public static FacesMessage getMessage(Locale locale, 
                                                 String messageId, 
                                                 Object... params) {       
        String summary = null;
        String detail = null;       
        ResourceBundle bundle;
        String bundleName;

        // see if we have a user-provided bundle
        if (null != (bundleName = getApplication().getMessageBundle())) {
            if (null != 
                (bundle = 
                    ResourceBundle.getBundle(bundleName, locale,
                      getCurrentLoader(bundleName)))) {
                // see if we have a hit
                try {
                    summary = bundle.getString(messageId);
                    detail = bundle.getString(messageId + "_detail");
                }
                catch (MissingResourceException e) {
                    // ignore
                }
            }
        }
    
        // we couldn't find a summary in the user-provided bundle
        if (null == summary) {
            // see if we have a summary in the app provided bundle
            bundle = ResourceBundle.getBundle(FacesMessage.FACES_MESSAGES, 
                                              locale,
                                              getCurrentLoader(bundleName));
            if (null == bundle) {
                throw new NullPointerException();
            }
            // see if we have a hit
            try {
                summary = bundle.getString(messageId);
                // we couldn't find a summary anywhere!  Return null
                if (null == summary) {
                    return null;
                }
                detail = bundle.getString(messageId + "_detail");
            } catch (MissingResourceException e) {
                // ignore
            }
        }
        // At this point, we have a summary and a bundle.     
        FacesMessage ret = new BindingFacesMessage(locale, summary, detail, params);
        ret.setSeverity(FacesMessage.SEVERITY_ERROR);
        return (ret);
    }


    /**
     * <p>Creates and returns a FacesMessage for the specified Locale.</p>
     *
     * @param context   - the <code>FacesContext</code> for the current request
     * @param messageId - the key of the message in the resource bundle
     * @param params    - substittion parameters
     *
     * @return a localized <code>FacesMessage</code> with the severity
     *  of FacesMessage.SEVERITY_ERROR
     */
    public static FacesMessage getMessage(FacesContext context, 
                                                String messageId,
                                                Object... params) {
                                                
        if (context == null || messageId == null ) {
            throw new NullPointerException(" context " 
                + context 
                + " messageId " 
                + messageId);
        }
        Locale locale;
        // viewRoot may not have been initialized at this point.
        if (context.getViewRoot() != null) {
            locale = context.getViewRoot().getLocale();
        } else {
            locale = Locale.getDefault();
        }
        
        if (null == locale) {
            throw new NullPointerException(" locale is null ");
        }
        
        FacesMessage message = getMessage(locale, messageId, params);
        if (message != null) {
            return message;
        }
        locale = Locale.getDefault();
        return (getMessage(locale, messageId, params));
    }  
                       

    /**
     * <p>Returns the <code>label</code> property from the specified
     * component.</p>
     *
     * @param context   - the <code>FacesContext</code> for the current request
     * @param component - the component of interest
     *
     * @return the label, if any, of the component
     */
    public static Object getLabel(FacesContext context, 
                                        UIComponent component) {
                                        
        Object o = component.getAttributes().get("label");
        if (o == null || (o instanceof String && ((String) o).length() == 0)) {
            o = component.getValueExpression("label");
        }
        // Use the "clientId" if there was no label specified.
        if (o == null) {
            o = component.getClientId(context);
        }
        return o;
    }

    protected static Application getApplication() {
        FacesContext context = FacesContext.getCurrentInstance();
        if (context != null) {
            return (FacesContext.getCurrentInstance().getApplication());
        }
        ApplicationFactory afactory = (ApplicationFactory)
            FactoryFinder.getFactory(FactoryFinder.APPLICATION_FACTORY);
        return (afactory.getApplication());
    }

    protected static ClassLoader getCurrentLoader(Object fallbackClass) {
        ClassLoader loader =
            Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = fallbackClass.getClass().getClassLoader();
        }
        return loader;
    }

    /**
     * This class overrides FacesMessage to provide the evaluation
     * of binding expressions in addition to Strings.
     * It is often the case, that a binding expression may reference
     * a localized property value that would be used as a 
     * substitution parameter in the message.  For example:
     *  <code>#{bundle.userLabel}</code>
     * "bundle" may not be available until the page is rendered.
     * The "late" binding evaluation in <code>getSummary</code> and 
     * <code>getDetail</code> allow the expression to be evaluated
     * when that property is available.
     */
    static class BindingFacesMessage extends FacesMessage {
        BindingFacesMessage(
            Locale locale,
            String messageFormat,
            String detailMessageFormat,
            // array of parameters, both Strings and ValueBindings
            Object[] parameters) {

            super(messageFormat, detailMessageFormat);
            this.locale = locale;
            this.parameters = parameters;
            if (parameters != null) {
                resolvedParameters = new Object[parameters.length];
            }
        }

        public String getSummary() {
            String pattern = super.getSummary();
            resolveBindings();
            return getFormattedString(pattern, resolvedParameters);
        }

        public String getDetail() {
            String pattern = super.getDetail();
            resolveBindings();
            return getFormattedString(pattern, resolvedParameters);
        }

        private void resolveBindings() {
            FacesContext context = null;
            if (parameters != null) {
                for (int i = 0; i < parameters.length; i++) {
                    Object o = parameters[i];
                    if (o instanceof ValueBinding) {
                        if (context == null) {
                            context = FacesContext.getCurrentInstance();
                        }
                        o = ((ValueBinding) o).getValue(context);
                    }
                    if (o instanceof ValueExpression) {
                        if (context == null) {
                            context = FacesContext.getCurrentInstance();
                        }
                        o = ((ValueExpression) o).getValue(context.getELContext());
                    }
                    // to avoid 'null' appearing in message
                    if (o == null) {
                        o = "";
                    }
                    resolvedParameters[i] = o;
                }
            }
        }

        private String getFormattedString(String msgtext, Object[] params) {
            String localizedStr = null;
                                                                                
            if (params == null || msgtext == null ) {
                return msgtext;
            }
            StringBuffer b = new StringBuffer(100);
            MessageFormat mf = new MessageFormat(msgtext);
            if (locale != null) {
                mf.setLocale(locale);
                b.append(mf.format(params));
                localizedStr = b.toString();
            }
            return localizedStr;
        }

        private Locale locale;
        private Object[] parameters;
        private Object[] resolvedParameters;
    }
    
} // end of class MessageFactory
