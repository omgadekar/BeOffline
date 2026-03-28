package com.beoffline.app.ui.screens;

import android.content.Context;
import com.beoffline.app.background.BackgroundProtectionManager;
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
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
  private final Provider<Context> contextProvider;

  private final Provider<BackgroundProtectionManager> backgroundProtectionManagerProvider;

  private final Provider<BlockRuleRepository> repositoryProvider;

  private final Provider<VpnController> vpnControllerProvider;

  private final Provider<VpnStateManager> vpnStateManagerProvider;

  public DashboardViewModel_Factory(Provider<Context> contextProvider,
      Provider<BackgroundProtectionManager> backgroundProtectionManagerProvider,
      Provider<BlockRuleRepository> repositoryProvider,
      Provider<VpnController> vpnControllerProvider,
      Provider<VpnStateManager> vpnStateManagerProvider) {
    this.contextProvider = contextProvider;
    this.backgroundProtectionManagerProvider = backgroundProtectionManagerProvider;
    this.repositoryProvider = repositoryProvider;
    this.vpnControllerProvider = vpnControllerProvider;
    this.vpnStateManagerProvider = vpnStateManagerProvider;
  }

  @Override
  public DashboardViewModel get() {
    return newInstance(contextProvider.get(), backgroundProtectionManagerProvider.get(), repositoryProvider.get(), vpnControllerProvider.get(), vpnStateManagerProvider.get());
  }

  public static DashboardViewModel_Factory create(Provider<Context> contextProvider,
      Provider<BackgroundProtectionManager> backgroundProtectionManagerProvider,
      Provider<BlockRuleRepository> repositoryProvider,
      Provider<VpnController> vpnControllerProvider,
      Provider<VpnStateManager> vpnStateManagerProvider) {
    return new DashboardViewModel_Factory(contextProvider, backgroundProtectionManagerProvider, repositoryProvider, vpnControllerProvider, vpnStateManagerProvider);
  }

  public static DashboardViewModel newInstance(Context context,
      BackgroundProtectionManager backgroundProtectionManager, BlockRuleRepository repository,
      VpnController vpnController, VpnStateManager vpnStateManager) {
    return new DashboardViewModel(context, backgroundProtectionManager, repository, vpnController, vpnStateManager);
  }
}
