/*
 * Copyright 2013 Jeanfrancois Arcand
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
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
 *
 */

package org.atmosphere.cpr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.atmosphere.cpr.FrameworkConfig.ATMOSPHERE_RESOURCE;

/**
 * A {@link Meteor} is a simple class that can be used from a {@link javax.servlet.Servlet}
 * to suspend, broadcast and resume a response. A {@link Meteor} can be created by invoking
 * the build() method.
 * <p><code>
 * Meteor.build(HttpServletRequest).suspend(-1);
 * </code></p><p>
 * A Meteor is usually created when an application need to suspend a response.
 * A Meteor instance can then be cached and re-used later for either
 * broadcasting a message, or when an application needs to resume the
 * suspended response.
 *
 * @author Jeanfrancois Arcand
 */
public class Meteor {

    private static final Logger logger = LoggerFactory.getLogger(Meteor.class);

    protected final static ConcurrentHashMap<AtmosphereResource, Meteor> cache =
            new ConcurrentHashMap<AtmosphereResource, Meteor>();
    private final AtmosphereResource r;
    private Object o;
    private AtomicBoolean isDestroyed = new AtomicBoolean(false);

    private Meteor(AtmosphereResource r,
                   List<BroadcastFilter> l, Serializer s) {

        this.r = r;
        this.r.setSerializer(s);
        if (l != null) {
            for (BroadcastFilter f : l) {
                this.r.getBroadcaster().getBroadcasterConfig().addFilter(f);
            }
        }
        cache.put(this.r, this);
    }

    /**
     * Retrieve an instance of {@link Meteor} based on the {@link HttpServletRequest}
     *
     * @param r {@link HttpServletRequest}
     * @return a {@link Meteor} or null if not found
     */
    public static Meteor lookup(HttpServletRequest r) {
        return cache.get(r.getAttribute(ATMOSPHERE_RESOURCE));
    }


    /**
     * Create a {@link Meteor} using the {@link HttpServletRequest}
     *
     * @param r an {@link HttpServletRequest}
     * @return a {@link Meteor} than can be used to resume, suspend and broadcast {@link Object}
     */
    public final static Meteor build(HttpServletRequest r) {
        return build(r, null);
    }

    /**
     * Create a {@link Meteor} using the {@link HttpServletRequest} and use the
     * {@link Serializer} for writting the result of a broadcast operation using
     * the {@link HttpServletResponse}
     *
     * @param r an {@link HttpServletRequest}
     * @param s a {@link Serializer} used when writing broadcast events.
     * @return a {@link Meteor} than can be used to resume, suspend and broadcast {@link Object}
     */
    public final static Meteor build(HttpServletRequest r, Serializer s) {
        return build(r, null, s);
    }

    /**
     * Create a {@link Meteor} using the {@link HttpServletRequest} and use a list of
     * {@link BroadcastFilter} and {@link Serializer} for writting the result
     * of a broadcast operation the {@link HttpServletResponse}.
     *
     * @param req an {@link HttpServletRequest}
     * @param l   a list of {@link BroadcastFilter}
     * @param s   a {@link Serializer} used when writing broadcast events.
     * @return a {@link Meteor} than can be used to resume, suspend and broadcast {@link Object}
     */
    public final static Meteor build(HttpServletRequest req, List<BroadcastFilter> l, Serializer s) {
        return build(req, Broadcaster.SCOPE.APPLICATION, l, s);
    }

    /**
     * Create a {@link Meteor} using the {@link HttpServletRequest} and use a list of
     * {@link BroadcastFilter} and {@link Serializer} for writting the result
     * of a broadcast operation the {@link HttpServletResponse}.
     *
     * @param req   an {@link HttpServletRequest}
     * @param scope the {@link Broadcaster.SCOPE}}
     * @param l     a list of {@link BroadcastFilter}
     * @param s     a {@link Serializer} used when writing broadcast events.
     * @return a {@link Meteor} than can be used to resume, suspend and broadcast {@link Object}
     */
    public final static Meteor build(HttpServletRequest req, Broadcaster.SCOPE scope,
                                     List<BroadcastFilter> l, Serializer s) {
        AtmosphereResource r =
                (AtmosphereResource)
                        req.getAttribute(ATMOSPHERE_RESOURCE);
        if (r == null) throw new IllegalStateException("MeteorServlet not defined in web.xml");

        Broadcaster b = null;

        if (scope == Broadcaster.SCOPE.REQUEST) {
            try {
                BroadcasterFactory f = r.getAtmosphereConfig().getBroadcasterFactory();
                b = f.get(DefaultBroadcaster.class, DefaultBroadcaster.class.getSimpleName() + UUID.randomUUID());
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
            b.setScope(scope);
            r.setBroadcaster(b);
            req.setAttribute(AtmosphereResourceImpl.SKIP_BROADCASTER_CREATION, Boolean.TRUE);
        }

        Meteor m = new Meteor(r, l, s);
        req.setAttribute(AtmosphereResourceImpl.METEOR, m);
        return m;
    }

    /**
     * Suspend the underlying {@link HttpServletResponse}. Passing value of -1
     * suspend the response forever.
     *
     * @param l the maximum time a response stay suspended.
     * @return {@link Meteor}
     */
    public Meteor suspend(long l) {
        if (destroyed()) return null;
        r.suspend(l);
        return this;
    }

    /**
     * Resume the Meteor after the first broadcast operation. This is useful when long-polling is used.
     * @param resumeOnBroadcast
     * @return this
     */
    public Meteor resumeOnBroadcast(boolean resumeOnBroadcast) {
        r.resumeOnBroadcast(resumeOnBroadcast);
        return this;
    }

    /**
     * Return the current {@link org.atmosphere.cpr.AtmosphereResource.TRANSPORT}. The transport needs to be
     * explicitly set by the client by adding the appropriate {@link HeaderConfig#X_ATMOSPHERE_TRANSPORT} value,
     * which can be long-polling, streaming, websocket or jsonp.
     * @return
     */
    public AtmosphereResource.TRANSPORT transport() {
        return r.transport();
    }

    /**
     * Suspend the underlying {@link HttpServletResponse}. Passing value of -1
     * suspend the response forever.
     *
     * @param timeout  the maximum time a response stay suspended.
     * @param timeunit The time unit of the timeout value
     * @return {@link Meteor}
     */

    public Meteor suspend(long timeout, TimeUnit timeunit) {
        if (destroyed()) return null;
        r.suspend(timeout, timeunit);
        return this;
    }

    /**
     * Resume the underlying {@link HttpServletResponse}
     *
     * @return {@link Meteor}
     */
    public Meteor resume() {
        if (destroyed()) return null;
        r.resume();
        cache.remove(r);
        return this;
    }

    /**
     * Broadcast an {@link Object} to all suspended response.
     *
     * @param o an {@link Object}
     * @return {@link Meteor}
     */
    public Meteor broadcast(Object o) {
        if (destroyed()) return null;
        r.getBroadcaster().broadcast(o);
        return this;
    }

    /**
     * Schedule a periodic broadcast, in seconds.
     *
     * @param o      an {@link Object}
     * @param period period in seconds
     * @return {@link Meteor}
     */
    public Meteor schedule(Object o, long period) {
        if (destroyed()) return null;
        r.getBroadcaster().scheduleFixedBroadcast(o, period, TimeUnit.SECONDS);
        return this;
    }

    /**
     * Schedule a delayed broadcast, in seconds.
     *
     * @param o      an {@link Object}
     * @param period period in seconds
     * @return {@link Meteor}
     */
    public Meteor delayBroadadcast(Object o, long period) {
        if (destroyed()) return null;
        r.getBroadcaster().delayBroadcast(o, period, TimeUnit.SECONDS);
        return this;
    }

    /**
     * Return the underlying {@link Broadcaster}
     *
     * @return
     */
    public Broadcaster getBroadcaster() {
        if (destroyed()) return null;
        return r.getBroadcaster();
    }

    /**
     * Set a {@link Broadcaster} instance.
     *
     * @param b
     */
    public void setBroadcaster(Broadcaster b) {
        if (destroyed()) return;
        r.setBroadcaster(b);
    }

    /**
     * Return an {@link Object} with this {@link Meteor}
     *
     * @return the {@link Object}
     */
    public Object attachement() {
        return o;
    }

    /**
     * Attach an {@link Object} with this {@link Meteor}
     *
     * @return the {@link Object}
     */
    public void attach(Object o) {
        this.o = o;
    }

    /**
     * Add a {@link AtmosphereResourceEventListener} which gets invoked when
     * response are resuming, when the remote client close the connection or
     * when the a {@link Broadcaster#broadcast} operations occurs.
     *
     * @param e an inatance of {@link AtmosphereResourceEventListener}
     */
    public Meteor addListener(AtmosphereResourceEventListener e) {
        if (!destroyed()) {
            r.addEventListener(e);
        }
        return this;
    }

    /**
     * Remove a {@link AtmosphereResourceEventListener} which gets invoked when
     * response are resuming, when the remote client close the connection or
     * when the a {@link Broadcaster#broadcast} operations occurs.
     *
     * @param e an inatance of {@link AtmosphereResourceEventListener}
     */
    public Meteor removeListener(AtmosphereResourceEventListener e) {
        if (!destroyed()){
            r.removeEventListener(e);
        }
        return this;
    }

    /**
     * Mark this instance as Destroyed. No more operation will be allowed.
     */
    public void destroy() {
        isDestroyed.set(true);
        cache.remove(r);
    }

    private boolean destroyed(){
        if (isDestroyed.get()) {
            logger.debug("This Meteor is destroyed and cannot be used.");
            return true;
        }
        return false;
    }

    /**
     * Return the underlying {@link AtmosphereResource}
     *
     * @return the underlying {@link AtmosphereResource}
     */
    public AtmosphereResource getAtmosphereResource() {
        return r;
    }

}
