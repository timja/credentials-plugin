/*
 * The MIT License
 *
 * Copyright (c) 2026, Jenkins project contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.cloudbees.plugins.credentials.cluster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import java.nio.charset.StandardCharsets;
import jenkins.persistence.InMemoryPersistenceStore;
import jenkins.persistence.PersistenceStore;
import jenkins.persistence.PersistenceStoreProvider;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * System credentials changed by another controller sharing a persistence store are applied here.
 */
@WithJenkins
class RemoteCredentialsChangesTest {

    static final InMemoryPersistenceStore STORE = new InMemoryPersistenceStore(key -> key.equals("credentials.xml"));

    @Test
    void credentialsChangedElsewhereAreApplied(JenkinsRule r) throws Exception {
        SystemCredentialsProvider p = SystemCredentialsProvider.getInstance();
        p.getCredentials()
                .add(new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "c1", "first", "user", "secret-1"));
        p.save();
        PersistenceStore.Entry saved = STORE.read("credentials.xml");
        assertNotNull(saved, "saved to the store");

        String xml = new String(saved.content(), StandardCharsets.UTF_8)
                .replace("<id>c1</id>", "<id>c2</id>")
                .replace("<description>first</description>", "<description>second</description>");
        STORE.as("other-controller")
                .write("credentials.xml", xml.getBytes(StandardCharsets.UTF_8), PersistenceStore.ANY);
        for (int i = 0;
                i < 100
                        && p.getCredentials().stream().noneMatch(c -> ((UsernamePasswordCredentialsImpl) c)
                                .getId()
                                .equals("c2"));
                i++) {
            Thread.sleep(50);
        }
        assertEquals(1, p.getCredentials().size());
        UsernamePasswordCredentialsImpl c =
                (UsernamePasswordCredentialsImpl) p.getCredentials().get(0);
        assertEquals("c2", c.getId());
        assertEquals("second", c.getDescription());
        assertEquals("secret-1", c.getPassword().getPlainText(), "the secret decrypts here");
    }

    @TestExtension
    public static class Provider implements PersistenceStoreProvider {
        @Override
        public PersistenceStore create() {
            return STORE;
        }
    }
}
