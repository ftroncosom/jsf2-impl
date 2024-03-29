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

package com.sun.faces.context;

import java.util.Enumeration;
import java.util.Map;
import java.util.Iterator;
import java.util.Collections;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.io.Serializable;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import com.sun.faces.util.FacesLogger;

/**
 * @see javax.faces.context.ExternalContext#getSessionMap()  
 */
public class SessionMap extends BaseContextMap<Object> {

    private static final Logger LOGGER = FacesLogger.APPLICATION.getLogger();

    private final HttpServletRequest request;

    // ------------------------------------------------------------ Constructors


    public SessionMap(HttpServletRequest request) {
        this.request = request;
    }


    // -------------------------------------------------------- Methods from Map


    @Override
    public void clear() {
        HttpSession session = getSession(false);
        if (session != null) {
            for (Enumeration e = session.getAttributeNames();
                 e.hasMoreElements();) {
                String name = (String) e.nextElement();
                session.removeAttribute(name);
            }
        }
    }


    // Supported by maps if overridden
    @Override
    public void putAll(Map t) {
        HttpSession session = getSession(true);
        for (Iterator i = t.entrySet().iterator(); i.hasNext(); ) {
            Map.Entry entry = (Map.Entry) i.next();
            Object v = entry.getValue();
            Object k = entry.getKey();
            if (!(v instanceof Serializable)) {
                LOGGER.log(Level.WARNING,
                           "jsf.context.extcontext.sessionmap.nonserializable",
                           new Object[] { k, v.getClass().getName() });
            }
            //noinspection NonSerializableObjectBoundToHttpSession
            session.setAttribute((String) k, v);
        }
    }


    @Override
    public Object get(Object key) {
        if (key == null) {
            throw new NullPointerException();
        }
        HttpSession session = getSession(false);
        return ((session != null) ? session.getAttribute(key.toString()) : null);

    }


    @Override
    public Object put(String key, Object value) {
        if (key == null) {
            throw new NullPointerException();
        }
        HttpSession session = getSession(true);
        Object result = session.getAttribute(key);
        if (!(value instanceof Serializable)) {
            LOGGER.log(Level.WARNING,
                       "jsf.context.extcontext.sessionmap.nonserializable",
                       new Object[]{key, value.getClass().getName()});
        }
        //noinspection NonSerializableObjectBoundToHttpSession
        session.setAttribute(key, value);
        return (result);
    }


    @Override
    public Object remove(Object key) {
        if (key == null) {
            return null;
        }
        HttpSession session = getSession(false);
        if (session != null) {
            String keyString = key.toString();
            Object result = session.getAttribute(keyString);
            session.removeAttribute(keyString);
            return (result);
        }
        return null;
    }


    @Override
    public boolean containsKey(Object key) {
        HttpSession session = getSession(false);
        return ((session != null)
                && session.getAttribute(key.toString()) != null);
    }


    @Override
    public boolean equals(Object obj) {
        return !(obj == null || !(obj instanceof SessionMap))
               && super.equals(obj);
    }


    @Override
    public int hashCode() {
        HttpSession session = getSession(false);
        int hashCode =
              7 * ((session != null) ? session.hashCode() : super.hashCode());
        if (session != null) {
            for (Iterator i = entrySet().iterator(); i.hasNext();) {
                hashCode += i.next().hashCode();
            }
        }
        return hashCode;
    }


    // --------------------------------------------- Methods from BaseContextMap


    @SuppressWarnings("unchecked")
    protected Iterator<Map.Entry<String,Object>> getEntryIterator() {
        HttpSession session = getSession(false);
        if (session != null) {
            return new EntryIterator(session.getAttributeNames());
        } else {
            Map<String,Object> empty = Collections.emptyMap();
            return empty.entrySet().iterator();
        }
    }


    @SuppressWarnings("unchecked")
    protected Iterator<String> getKeyIterator() {
        HttpSession session = getSession(false);
        if (session != null) {
            return new KeyIterator(session.getAttributeNames());
        } else {
            Map<String,Object> empty = Collections.emptyMap();
            return empty.keySet().iterator();
        }
    }


    @SuppressWarnings("unchecked")
    protected Iterator<Object> getValueIterator() {
        HttpSession session = getSession(false);
         if (session != null) {
            return new ValueIterator(session.getAttributeNames());
        } else {
            Map<String,Object> empty = Collections.emptyMap();
            return empty.values().iterator();
        }
    }


    // --------------------------------------------------------- Private Methods


    private HttpSession getSession(boolean createNew) {
        return request.getSession(createNew);
    }

} // END SessionMap
