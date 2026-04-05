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
public final class StartRuleWorker_AssistedFactory_Impl implements StartRuleWorker_AssistedFactory {
  private final StartRuleWorker_Factory delegateFactory;

  StartRuleWorker_AssistedFactory_Impl(StartRuleWorker_Factory delegateFactory) {
    this.delegateFactory = delegateFactory;
  }

  @Override
  public StartRuleWorker create(Context p0, WorkerParameters p1) {
    return delegateFactory.get(p0, p1);
  }

  public static Provider<StartRuleWorker_AssistedFactory> create(
      StartRuleWorker_Factory delegateFactory) {
    return InstanceFactory.create(new StartRuleWorker_AssistedFactory_Impl(delegateFactory));
  }

  public static dagger.internal.Provider<StartRuleWorker_AssistedFactory> createFactoryProvider(
      StartRuleWorker_Factory delegateFactory) {
    return InstanceFactory.create(new StartRuleWorker_AssistedFactory_Impl(delegateFactory));
  }
}
