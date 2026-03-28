package com.beoffline.app.vpn;

import com.beoffline.app.data.repository.BlockRuleRepository;
import com.beoffline.app.support.BlockedTrafficAlertManager;
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
public final class BeOfflineVpnService_MembersInjector implements MembersInjector<BeOfflineVpnService> {
  private final Provider<VpnStateManager> vpnStateManagerProvider;

  private final Provider<BlockRuleRepository> repositoryProvider;

  private final Provider<BlockedTrafficAlertManager> blockedTrafficAlertManagerProvider;

  public BeOfflineVpnService_MembersInjector(Provider<VpnStateManager> vpnStateManagerProvider,
      Provider<BlockRuleRepository> repositoryProvider,
      Provider<BlockedTrafficAlertManager> blockedTrafficAlertManagerProvider) {
    this.vpnStateManagerProvider = vpnStateManagerProvider;
    this.repositoryProvider = repositoryProvider;
    this.blockedTrafficAlertManagerProvider = blockedTrafficAlertManagerProvider;
  }

  public static MembersInjector<BeOfflineVpnService> create(
      Provider<VpnStateManager> vpnStateManagerProvider,
      Provider<BlockRuleRepository> repositoryProvider,
      Provider<BlockedTrafficAlertManager> blockedTrafficAlertManagerProvider) {
    return new BeOfflineVpnService_MembersInjector(vpnStateManagerProvider, repositoryProvider, blockedTrafficAlertManagerProvider);
  }

  @Override
  public void injectMembers(BeOfflineVpnService instance) {
    injectVpnStateManager(instance, vpnStateManagerProvider.get());
    injectRepository(instance, repositoryProvider.get());
    injectBlockedTrafficAlertManager(instance, blockedTrafficAlertManagerProvider.get());
  }

  @InjectedFieldSignature("com.beoffline.app.vpn.BeOfflineVpnService.vpnStateManager")
  public static void injectVpnStateManager(BeOfflineVpnService instance,
      VpnStateManager vpnStateManager) {
    instance.vpnStateManager = vpnStateManager;
  }

  @InjectedFieldSignature("com.beoffline.app.vpn.BeOfflineVpnService.repository")
  public static void injectRepository(BeOfflineVpnService instance,
      BlockRuleRepository repository) {
    instance.repository = repository;
  }

  @InjectedFieldSignature("com.beoffline.app.vpn.BeOfflineVpnService.blockedTrafficAlertManager")
  public static void injectBlockedTrafficAlertManager(BeOfflineVpnService instance,
      BlockedTrafficAlertManager blockedTrafficAlertManager) {
    instance.blockedTrafficAlertManager = blockedTrafficAlertManager;
  }
}
