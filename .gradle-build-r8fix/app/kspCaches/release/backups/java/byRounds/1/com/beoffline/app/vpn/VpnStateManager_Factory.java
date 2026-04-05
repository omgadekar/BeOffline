package com.beoffline.app.vpn;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
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
public final class VpnStateManager_Factory implements Factory<VpnStateManager> {
  @Override
  public VpnStateManager get() {
    return newInstance();
  }

  public static VpnStateManager_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static VpnStateManager newInstance() {
    return new VpnStateManager();
  }

  private static final class InstanceHolder {
    private static final VpnStateManager_Factory INSTANCE = new VpnStateManager_Factory();
  }
}
