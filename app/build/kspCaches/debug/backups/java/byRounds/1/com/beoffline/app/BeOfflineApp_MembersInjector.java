package com.beoffline.app;

import androidx.hilt.work.HiltWorkerFactory;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class BeOfflineApp_MembersInjector implements MembersInjector<BeOfflineApp> {
  private final Provider<HiltWorkerFactory> workerFactoryProvider;

  public BeOfflineApp_MembersInjector(Provider<HiltWorkerFactory> workerFactoryProvider) {
    this.workerFactoryProvider = workerFactoryProvider;
  }

  public static MembersInjector<BeOfflineApp> create(
      Provider<HiltWorkerFactory> workerFactoryProvider) {
    return new BeOfflineApp_MembersInjector(workerFactoryProvider);
  }

  @Override
  public void injectMembers(BeOfflineApp instance) {
    injectWorkerFactory(instance, workerFactoryProvider.get());
  }

  @InjectedFieldSignature("com.beoffline.app.BeOfflineApp.workerFactory")
  public static void injectWorkerFactory(BeOfflineApp instance, HiltWorkerFactory workerFactory) {
    instance.workerFactory = workerFactory;
  }
}
