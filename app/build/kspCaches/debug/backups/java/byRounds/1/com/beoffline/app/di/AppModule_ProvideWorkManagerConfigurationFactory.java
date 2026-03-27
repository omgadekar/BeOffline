package com.beoffline.app.di;

import androidx.hilt.work.HiltWorkerFactory;
import androidx.work.Configuration;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
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
public final class AppModule_ProvideWorkManagerConfigurationFactory implements Factory<Configuration> {
  private final Provider<HiltWorkerFactory> workerFactoryProvider;

  public AppModule_ProvideWorkManagerConfigurationFactory(
      Provider<HiltWorkerFactory> workerFactoryProvider) {
    this.workerFactoryProvider = workerFactoryProvider;
  }

  @Override
  public Configuration get() {
    return provideWorkManagerConfiguration(workerFactoryProvider.get());
  }

  public static AppModule_ProvideWorkManagerConfigurationFactory create(
      Provider<HiltWorkerFactory> workerFactoryProvider) {
    return new AppModule_ProvideWorkManagerConfigurationFactory(workerFactoryProvider);
  }

  public static Configuration provideWorkManagerConfiguration(HiltWorkerFactory workerFactory) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideWorkManagerConfiguration(workerFactory));
  }
}
