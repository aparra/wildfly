/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.wildfly.clustering.web.infinispan.sso.coarse;

import java.util.HashMap;
import java.util.Map;

import org.infinispan.Cache;
import org.infinispan.context.Flag;
import org.wildfly.clustering.ee.infinispan.CacheEntryMutator;
import org.wildfly.clustering.ee.infinispan.Mutator;
import org.wildfly.clustering.web.LocalContextFactory;
import org.wildfly.clustering.web.infinispan.sso.InfinispanSSO;
import org.wildfly.clustering.web.infinispan.sso.SSOFactory;
import org.wildfly.clustering.web.sso.SSO;
import org.wildfly.clustering.web.sso.Sessions;

public class CoarseSSOFactory<I, D, L> implements SSOFactory<CoarseSSOEntry<I, D, L>, I, D, L> {

    private final Cache<String, CoarseAuthenticationEntry<I, D, L>> authenticationCache;
    private final Cache<CoarseSessionsKey, Map<D, String>> sessionsCache;
    private final LocalContextFactory<L> localContextFactory;

    public CoarseSSOFactory(Cache<String, CoarseAuthenticationEntry<I, D, L>> authenticationCache, Cache<CoarseSessionsKey, Map<D, String>> sessionsCache, LocalContextFactory<L> localContextFactory) {
        this.authenticationCache = authenticationCache;
        this.sessionsCache = sessionsCache;
        this.localContextFactory = localContextFactory;
    }

    @Override
    public SSO<I, D, L> createSSO(String id, CoarseSSOEntry<I, D, L> entry) {
        CoarseAuthenticationEntry<I, D, L> authenticationEntry = entry.getAuthenticationEntry();
        CoarseSessionsKey sessionsKey = new CoarseSessionsKey(id);
        Map<D, String> sessionsValue = entry.getSessions();
        Mutator sessionsMutator = new CacheEntryMutator<>(this.sessionsCache, sessionsKey, sessionsValue);
        Sessions<D> sessions = new CoarseSessions<>(sessionsValue, sessionsMutator);
        return new InfinispanSSO<>(id, authenticationEntry.getAuthentication(), sessions, authenticationEntry.getLocalContext(), this.localContextFactory, this);
    }

    @Override
    public CoarseSSOEntry<I, D, L> createValue(String id) {
        CoarseAuthenticationEntry<I, D, L> entry = new CoarseAuthenticationEntry<>();
        CoarseAuthenticationEntry<I, D, L> existingEntry = this.authenticationCache.getAdvancedCache().withFlags(Flag.FORCE_SYNCHRONOUS).putIfAbsent(id, entry);
        if (existingEntry != null) {
            Map<D, String> value = this.sessionsCache.get(new CoarseSessionsKey(id));
            return new CoarseSSOEntry<>(existingEntry, value);
        }
        Map<D, String> map = new HashMap<>();
        Map<D, String> existingMap = this.sessionsCache.getAdvancedCache().withFlags(Flag.FORCE_SYNCHRONOUS).putIfAbsent(new CoarseSessionsKey(id), map);
        return new CoarseSSOEntry<>(entry, (existingMap != null) ? existingMap : map);
    }

    @Override
    public CoarseSSOEntry<I, D, L> findValue(String id) {
        CoarseAuthenticationEntry<I, D, L> entry = this.authenticationCache.get(id);
        if (entry == null) return null;
        Map<D, String> map = this.sessionsCache.get(new CoarseSessionsKey(id));
        return new CoarseSSOEntry<>(entry, map);
    }

    @Override
    public void remove(String id) {
        this.authenticationCache.getAdvancedCache().withFlags(Flag.IGNORE_RETURN_VALUES).remove(id);
        this.sessionsCache.getAdvancedCache().withFlags(Flag.IGNORE_RETURN_VALUES).remove(new CoarseSessionsKey(id));
    }
}
