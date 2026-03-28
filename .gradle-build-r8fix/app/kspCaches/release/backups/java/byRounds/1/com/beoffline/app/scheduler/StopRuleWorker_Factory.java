package com.beoffline.app.scheduler;

import android.content.Context;
import androidx.work.WorkerParameters;
import com.beoffline.app.data.repository.BlockRuleRepository;
import com.beoffline.app.vpn.VpnController;
import dagger.internal.DaggerGenerated;
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
public final class StopRuleWorker_Factory {
  private final Provider<BlockRuleRepository> repositoryProvider;

  private final Provider<VpnController> vpnControllerProvider;

  public StopRuleWorker_Factory(Provider<BlockRuleRepository> repositoryProvider,
      Provider<VpnController> vpnControllerProvider) {
    this.repositoryProvider = repositoryProvider;
    this.vpnControllerProvider = vpnControllerProvider;
  }

  public StopRuleWorker get(Context context, WorkerParameters params) {
    return newInstance(context, params, repositoryProvider.get(), vpnControllerProvider.get());
  }

  public static StopRuleWorker_Factory create(Provider<BlockRuleRepository> repositoryProvider,
      Provider<VpnController> vpnControllerProvider) {
    return new StopRuleWorker_Factory(repositoryProvider, vpnControllerProvider);
  }

  public static StopRuleWorker newInstance(Context context, WorkerParameters params,
      BlockRuleRepository repository, VpnController vpnController) {
    return new StopRuleWorker(context, params, repository, vpnController);
  }
}
