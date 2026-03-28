package com.beoffline.app.vpn;

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
    topLevelClass = VpnHealthWorker.class
)
public interface VpnHealthWorker_HiltModule {
  @Binds
  @IntoMap
  @StringKey("com.beoffline.app.vpn.VpnHealthWorker")
  WorkerAssistedFactory<? extends ListenableWorker> bind(VpnHealthWorker_AssistedFactory factory);
}
