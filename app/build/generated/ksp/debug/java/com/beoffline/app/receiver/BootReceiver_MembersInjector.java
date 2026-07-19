package com.beoffline.app.receiver;

import com.beoffline.app.data.repository.BlockRuleRepository;
import com.beoffline.app.data.repository.OpenBlockRuleRepository;
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
public final class BootReceiver_MembersInjector implements MembersInjector<BootReceiver> {
  private final Provider<BlockRuleRepository> repositoryProvider;

  private final Provider<OpenBlockRuleRepository> openBlockRepositoryProvider;

  private final Provider<VpnController> vpnControllerProvider;

  public BootReceiver_MembersInjector(Provider<BlockRuleRepository> repositoryProvider,
      Provider<OpenBlockRuleRepository> openBlockRepositoryProvider,
      Provider<VpnController> vpnControllerProvider) {
    this.repositoryProvider = repositoryProvider;
    this.openBlockRepositoryProvider = openBlockRepositoryProvider;
    this.vpnControllerProvider = vpnControllerProvider;
  }

  public static MembersInjector<BootReceiver> create(
      Provider<BlockRuleRepository> repositoryProvider,
      Provider<OpenBlockRuleRepository> openBlockRepositoryProvider,
      Provider<VpnController> vpnControllerProvider) {
    return new BootReceiver_MembersInjector(repositoryProvider, openBlockRepositoryProvider, vpnControllerProvider);
  }

  @Override
  public void injectMembers(BootReceiver instance) {
    injectRepository(instance, repositoryProvider.get());
    injectOpenBlockRepository(instance, openBlockRepositoryProvider.get());
    injectVpnController(instance, vpnControllerProvider.get());
  }

  @InjectedFieldSignature("com.beoffline.app.receiver.BootReceiver.repository")
  public static void injectRepository(BootReceiver instance, BlockRuleRepository repository) {
    instance.repository = repository;
  }

  @InjectedFieldSignature("com.beoffline.app.receiver.BootReceiver.openBlockRepository")
  public static void injectOpenBlockRepository(BootReceiver instance,
      OpenBlockRuleRepository openBlockRepository) {
    instance.openBlockRepository = openBlockRepository;
  }

  @InjectedFieldSignature("com.beoffline.app.receiver.BootReceiver.vpnController")
  public static void injectVpnController(BootReceiver instance, VpnController vpnController) {
    instance.vpnController = vpnController;
  }
}
