package com.beoffline.app.scheduler;

import androidx.hilt.work.WorkerAssistedFactory;
import androidx.work.ListenableWorker;
import dagger.Binds;
import dagger.Module;
import dagger.hilt.InstallIn;
import dagger.hilt.codegen.OriginatingElement;
import dagger.hilt.components.SingletonComponent;
import dagger.multibindings.IntoMap;
import dagger.multibindings.StringKey;
import javax.annotation.processing.Generated;

@Generated("androidx.hilt.AndroidXHiltProcessor")
@Module
@InstallIn(SingletonComponent.class)
@OriginatingElement(
    topLevelClass = StopRuleWorker.class
)
public interface StopRuleWorker_HiltModule {
  @Binds
  @IntoMap
  @StringKey("com.beoffline.app.scheduler.StopRuleWorker")
  WorkerAssistedFactory<? extends ListenableWorker> bind(StopRuleWorker_AssistedFactory factory);
}
