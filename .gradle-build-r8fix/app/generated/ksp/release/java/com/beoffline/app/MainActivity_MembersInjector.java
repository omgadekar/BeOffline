package com.beoffline.app;

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
public final class MainActivity_MembersInjector implements MembersInjector<MainActivity> {
  private final Provider<VpnController> vpnControllerProvider;

  public MainActivity_MembersInjector(Provider<VpnController> vpnControllerProvider) {
    this.vpnControllerProvider = vpnControllerProvider;
  }

  public static MembersInjector<MainActivity> create(
      Provider<VpnController> vpnControllerProvider) {
    return new MainActivity_MembersInjector(vpnControllerProvider);
  }

  @Override
  public void injectMembers(MainActivity instance) {
    injectVpnController(instance, vpnControllerProvider.get());
  }

  @InjectedFieldSignature("com.beoffline.app.MainActivity.vpnController")
  public static void injectVpnController(MainActivity instance, VpnController vpnController) {
    instance.vpnController = vpnController;
  }
}
