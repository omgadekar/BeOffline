package com.beoffline.app.vpn;

import android.content.Context;
import androidx.work.WorkerParameters;
import com.beoffline.app.data.repository.BlockRuleRepository;
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
public final class VpnHealthWorker_Factory {
  private final Provider<BlockRuleRepository> repositoryProvider;

  private final Provider<VpnController> vpnControllerProvider;

  private final Provider<VpnStateManager> vpnStateManagerProvider;

  public VpnHealthWorker_Factory(Provider<BlockRuleRepository> repositoryProvider,
      Provider<VpnController> vpnControllerProvider,
      Provider<VpnStateManager> vpnStateManagerProvider) {
    this.repositoryProvider = repositoryProvider;
    this.vpnControllerProvider = vpnControllerProvider;
    this.vpnStateManagerProvider = vpnStateManagerProvider;
  }

  public VpnHealthWorker get(Context context, WorkerParameters params) {
    return newInstance(context, params, repositoryProvider.get(), vpnControllerProvider.get(), vpnStateManagerProvider.get());
  }

  public static VpnHealthWorker_Factory create(Provider<BlockRuleRepository> repositoryProvider,
      Provider<VpnController> vpnControllerProvider,
      Provider<VpnStateManager> vpnStateManagerProvider) {
    return new VpnHealthWorker_Factory(repositoryProvider, vpnControllerProvider, vpnStateManagerProvider);
  }

  public static VpnHealthWorker newInstance(Context context, WorkerParameters params,
      BlockRuleRepository repository, VpnController vpnController,
      VpnStateManager vpnStateManager) {
    return new VpnHealthWorker(context, params, repository, vpnController, vpnStateManager);
  }
}
