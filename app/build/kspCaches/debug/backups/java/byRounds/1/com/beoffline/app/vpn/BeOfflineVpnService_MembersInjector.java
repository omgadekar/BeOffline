package com.beoffline.app.vpn;

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

  public BeOfflineVpnService_MembersInjector(Provider<VpnStateManager> vpnStateManagerProvider) {
    this.vpnStateManagerProvider = vpnStateManagerProvider;
  }

  public static MembersInjector<BeOfflineVpnService> create(
      Provider<VpnStateManager> vpnStateManagerProvider) {
    return new BeOfflineVpnService_MembersInjector(vpnStateManagerProvider);
  }

  @Override
  public void injectMembers(BeOfflineVpnService instance) {
    injectVpnStateManager(instance, vpnStateManagerProvider.get());
  }

  @InjectedFieldSignature("com.beoffline.app.vpn.BeOfflineVpnService.vpnStateManager")
  public static void injectVpnStateManager(BeOfflineVpnService instance,
      VpnStateManager vpnStateManager) {
    instance.vpnStateManager = vpnStateManager;
  }
}
