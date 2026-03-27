package com.beoffline.app.scheduler;

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
public final class StopRuleWorker_AssistedFactory_Impl implements StopRuleWorker_AssistedFactory {
  private final StopRuleWorker_Factory delegateFactory;

  StopRuleWorker_AssistedFactory_Impl(StopRuleWorker_Factory delegateFactory) {
    this.delegateFactory = delegateFactory;
  }

  @Override
  public StopRuleWorker create(Context p0, WorkerParameters p1) {
    return delegateFactory.get(p0, p1);
  }

  public static Provider<StopRuleWorker_AssistedFactory> create(
      StopRuleWorker_Factory delegateFactory) {
    return InstanceFactory.create(new StopRuleWorker_AssistedFactory_Impl(delegateFactory));
  }

  public static dagger.internal.Provider<StopRuleWorker_AssistedFactory> createFactoryProvider(
      StopRuleWorker_Factory delegateFactory) {
    return InstanceFactory.create(new StopRuleWorker_AssistedFactory_Impl(delegateFactory));
  }
}
