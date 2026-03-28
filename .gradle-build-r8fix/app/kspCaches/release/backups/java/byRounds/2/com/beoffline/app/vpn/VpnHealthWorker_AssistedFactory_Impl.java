package com.beoffline.app.vpn;

import android.content.Context;
import androidx.work.WorkerParameters;
import dagger.internal.DaggerGenerated;
import dagger.internal.InstanceFactory;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class VpnHealthWorker_AssistedFactory_Impl implements VpnHealthWorker_AssistedFactory {
  private final VpnHealthWorker_Factory delegateFactory;

  VpnHealthWorker_AssistedFactory_Impl(VpnHealthWorker_Factory delegateFactory) {
    this.delegateFactory = delegateFactory;
  }

  @Override
  public VpnHealthWorker create(Context p0, WorkerParameters p1) {
    return delegateFactory.get(p0, p1);
  }

  public static Provider<VpnHealthWorker_AssistedFactory> create(
      VpnHealthWorker_Factory delegateFactory) {
    return InstanceFactory.create(new VpnHealthWorker_AssistedFactory_Impl(delegateFactory));
  }

  public static dagger.internal.Provider<VpnHealthWorker_AssistedFactory> createFactoryProvider(
      VpnHealthWorker_Factory delegateFactory) {
    return InstanceFactory.create(new VpnHealthWorker_AssistedFactory_Impl(delegateFactory));
  }
}
