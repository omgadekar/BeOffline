package com.beoffline.app.support;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation"
})
public final class BlockedTrafficAlertManager_Factory implements Factory<BlockedTrafficAlertManager> {
  private final Provider<Context> contextProvider;

  public BlockedTrafficAlertManager_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public BlockedTrafficAlertManager get() {
    return newInstance(contextProvider.get());
  }

  public static BlockedTrafficAlertManager_Factory create(Provider<Context> contextProvider) {
    return new BlockedTrafficAlertManager_Factory(contextProvider);
  }

  public static BlockedTrafficAlertManager newInstance(Context context) {
    return new BlockedTrafficAlertManager(context);
  }
}
