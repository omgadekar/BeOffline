package com.beoffline.app.ui.screens;

import com.beoffline.app.data.repository.BlockRuleRepository;
import com.beoffline.app.vpn.VpnController;
import com.beoffline.app.vpn.VpnStateManager;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class DashboardViewModel_Factory implements Factory<DashboardViewModel> {
  private final Provider<BlockRuleRepository> repositoryProvider;

  private final Provider<VpnController> vpnControllerProvider;

  private final Provider<VpnStateManager> vpnStateManagerProvider;

  public DashboardViewModel_Factory(Provider<BlockRuleRepository> repositoryProvider,
      Provider<VpnController> vpnControllerProvider,
      Provider<VpnStateManager> vpnStateManagerProvider) {
    this.repositoryProvider = repositoryProvider;
    this.vpnControllerProvider = vpnControllerProvider;
    this.vpnStateManagerProvider = vpnStateManagerProvider;
  }

  @Override
  public DashboardViewModel get() {
    return newInstance(repositoryProvider.get(), vpnControllerProvider.get(), vpnStateManagerProvider.get());
  }

  public static DashboardViewModel_Factory create(Provider<BlockRuleRepository> repositoryProvider,
      Provider<VpnController> vpnControllerProvider,
      Provider<VpnStateManager> vpnStateManagerProvider) {
    return new DashboardViewModel_Factory(repositoryProvider, vpnControllerProvider, vpnStateManagerProvider);
  }

  public static DashboardViewModel newInstance(BlockRuleRepository repository,
      VpnController vpnController, VpnStateManager vpnStateManager) {
    return new DashboardViewModel(repository, vpnController, vpnStateManager);
  }
}
