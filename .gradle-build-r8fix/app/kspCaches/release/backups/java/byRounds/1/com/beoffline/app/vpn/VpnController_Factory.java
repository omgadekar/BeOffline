package com.beoffline.app.vpn;

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
public final class VpnController_Factory implements Factory<VpnController> {
  private final Provider<Context> contextProvider;

  private final Provider<VpnStateManager> stateManagerProvider;

  public VpnController_Factory(Provider<Context> contextProvider,
      Provider<VpnStateManager> stateManagerProvider) {
    this.contextProvider = contextProvider;
    this.stateManagerProvider = stateManagerProvider;
  }

  @Override
  public VpnController get() {
    return newInstance(contextProvider.get(), stateManagerProvider.get());
  }

  public static VpnController_Factory create(Provider<Context> contextProvider,
      Provider<VpnStateManager> stateManagerProvider) {
    return new VpnController_Factory(contextProvider, stateManagerProvider);
  }

  public static VpnController newInstance(Context context, VpnStateManager stateManager) {
    return new VpnController(context, stateManager);
  }
}
