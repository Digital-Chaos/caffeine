/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.benmanes.caffeine.jcache;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import javax.cache.Cache;
import javax.cache.configuration.Configuration;
import javax.cache.configuration.MutableCacheEntryListenerConfiguration;
import javax.cache.event.CacheEntryListener;
import javax.cache.expiry.ExpiryPolicy;
import javax.cache.integration.CacheLoader;
import javax.cache.integration.CacheWriter;

import org.mockito.Mockito;
import org.testng.annotations.Test;

import com.github.benmanes.caffeine.jcache.configuration.CaffeineConfiguration;

/**
 * @author github.com/kdombeck (Ken Dombeck)
 */
public final class CacheProxyTest extends AbstractJCacheTest {
  private CloseableCacheEntryListener listener = Mockito.mock(CloseableCacheEntryListener.class);
  private CloseableExpiryPolicy expiry = Mockito.mock(CloseableExpiryPolicy.class);
  private CloseableCacheLoader loader = Mockito.mock(CloseableCacheLoader.class);
  private CloseableCacheWriter writer = Mockito.mock(CloseableCacheWriter.class);

  @Override
  protected CaffeineConfiguration<Integer, Integer> getConfiguration() {
    Mockito.reset(listener, expiry, loader, writer);

    var configuration = new CaffeineConfiguration<Integer, Integer>();
    configuration.setExpiryPolicyFactory(() -> expiry);
    configuration.setCacheLoaderFactory(() -> loader);
    configuration.setCacheWriterFactory(() -> writer);
    configuration.setWriteThrough(true);
    configuration.setReadThrough(true);
    return configuration;
  }

  @Test
  @SuppressWarnings({"ObjectToString", "unchecked"})
  public void getConfiguration_immutable() {
    var config = jcache.getConfiguration(CaffeineConfiguration.class);
    var type = UnsupportedOperationException.class;

    assertThrows(type, () -> config.getCacheEntryListenerConfigurations().iterator().remove());
    assertThrows(type, () -> config.addCacheEntryListenerConfiguration(null));
    assertThrows(type, () -> config.setCacheLoaderFactory(null));
    assertThrows(type, () -> config.setCacheWriterFactory(null));
    assertThrows(type, () -> config.setCopierFactory(null));
    assertThrows(type, () -> config.setExecutorFactory(null));
    assertThrows(type, () -> config.setExpireAfterAccess(OptionalLong.empty()));
    assertThrows(type, () -> config.setExpireAfterWrite(OptionalLong.empty()));
    assertThrows(type, () -> config.setExpiryFactory(Optional.empty()));
    assertThrows(type, () -> config.setExpiryPolicyFactory(null));
    assertThrows(type, () -> config.setManagementEnabled(false));
    assertThrows(type, () -> config.setMaximumSize(OptionalLong.empty()));
    assertThrows(type, () -> config.setMaximumWeight(OptionalLong.empty()));
    assertThrows(type, () -> config.setNativeStatisticsEnabled(false));
    assertThrows(type, () -> config.setReadThrough(false));
    assertThrows(type, () -> config.setRefreshAfterWrite(OptionalLong.empty()));
    assertThrows(type, () -> config.setSchedulerFactory(null));
    assertThrows(type, () -> config.setStatisticsEnabled(false));
    assertThrows(type, () -> config.setStoreByValue(false));
    assertThrows(type, () -> config.setTickerFactory(null));
    assertThrows(type, () -> config.setTypes(String.class, String.class));
    assertThrows(type, () -> config.setWeigherFactory(Optional.empty()));
    assertThrows(type, () -> config.setWriteThrough(false));

    assertThat(config).isEqualTo(jcacheConfiguration);
    assertThat(config.toString()).isEqualTo(jcacheConfiguration.toString());

    var configuration = new MutableCacheEntryListenerConfiguration<Integer, Integer>(
        /* listener */ () -> listener, /* filter */ () -> event -> true,
        /* isOldValueRequired */ false, /* isSynchronous */ false);
    jcache.registerCacheEntryListener(configuration);
    assertThat(config.getCacheEntryListenerConfigurations()).hasSize(0);

    var config2 = jcache.getConfiguration(CaffeineConfiguration.class);
    assertThat(config2).isNotEqualTo(config);
    assertThat(config2.getCacheEntryListenerConfigurations()
        .spliterator().estimateSize()).isEqualTo(1);
    assertThat(config2.getCacheEntryListenerConfigurations()).hasSize(1);
    assertThat(config2.getCacheEntryListenerConfigurations().toString())
        .isEqualTo(List.of(configuration).toString());
  }

  @Test
  public void unwrap_fail() {
    assertThrows(IllegalArgumentException.class, () -> jcache.unwrap(CaffeineConfiguration.class));
  }

  @Test
  public void unwrap() {
    assertThat(jcache.unwrap(Cache.class)).isSameInstanceAs(jcache);
    assertThat(jcache.unwrap(CacheProxy.class)).isSameInstanceAs(jcache);
    assertThat(jcache.unwrap(com.github.benmanes.caffeine.cache.Cache.class))
        .isSameInstanceAs(jcache.cache);
  }

  @Test
  public void unwrap_configuration() {
    abstract class Dummy implements Configuration<Integer, Integer> {
      private static final long serialVersionUID = 1L;
    };
    assertThrows(IllegalArgumentException.class, () -> jcache.getConfiguration(Dummy.class));
  }

  @Test
  public void unwrap_entry() {
    jcache.put(KEY_1, VALUE_1);
    var item = jcache.iterator().next();
    assertThrows(IllegalArgumentException.class, () -> item.unwrap(String.class));
  }

  @Test
  public void close_fails() throws IOException {
    doThrow(IOException.class).when(expiry).close();
    doThrow(IOException.class).when(loader).close();
    doThrow(IOException.class).when(writer).close();
    doThrow(IOException.class).when(listener).close();

    var configuration = new MutableCacheEntryListenerConfiguration<Integer, Integer>(
        /* listener */ () -> listener, /* filter */ () -> event -> true,
        /* isOldValueRequired */ false, /* isSynchronous */ false);
    jcache.registerCacheEntryListener(configuration);

    jcache.close();
    verify(expiry, atLeastOnce()).close();
    verify(loader, atLeastOnce()).close();
    verify(writer, atLeastOnce()).close();
    verify(listener, atLeastOnce()).close();
  }

  interface CloseableExpiryPolicy extends ExpiryPolicy, Closeable {}
  interface CloseableCacheLoader extends CacheLoader<Integer, Integer>, Closeable {}
  interface CloseableCacheWriter extends CacheWriter<Integer, Integer>, Closeable {}
  interface CloseableCacheEntryListener extends CacheEntryListener<Integer, Integer>, Closeable {}
}
