package com.beoffline.app.receiver;

import com.beoffline.app.data.repository.BlockRuleRepository;
import com.beoffline.app.vpn.VpnController;
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
public final class VpnRecoveryReceiver_MembersInjector implements MembersInjector<VpnRecoveryReceiver> {
  private final Provider<BlockRuleRepository> repositoryProvider;

  private final Provider<VpnController> vpnControllerProvider;

  public VpnRecoveryReceiver_MembersInjector(Provider<BlockRuleRepository> repositoryProvider,
      Provider<VpnController> vpnControllerProvider) {
    this.repositoryProvider = repositoryProvider;
    this.vpnControllerProvider = vpnControllerProvider;
  }

  public static MembersInjector<VpnRecoveryReceiver> create(
      Provider<BlockRuleRepository> repositoryProvider,
      Provider<VpnController> vpnControllerProvider) {
    return new VpnRecoveryReceiver_MembersInjector(repositoryProvider, vpnControllerProvider);
  }

  @Override
  public void injectMembers(VpnRecoveryReceiver instance) {
    injectRepository(instance, repositoryProvider.get());
    injectVpnController(instance, vpnControllerProvider.get());
  }

  @InjectedFieldSignature("com.beoffline.app.receiver.VpnRecoveryReceiver.repository")
  public static void injectRepository(VpnRecoveryReceiver instance,
      BlockRuleRepository repository) {
    instance.repository = repository;
  }

  @InjectedFieldSignature("com.beoffline.app.receiver.VpnRecoveryReceiver.vpnController")
  public static void injectVpnController(VpnRecoveryReceiver instance,
      VpnController vpnController) {
    instance.vpnController = vpnController;
  }
}
