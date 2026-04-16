package com.beoffline.app;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.view.View;
import androidx.fragment.app.Fragment;
import androidx.hilt.work.HiltWorkerFactory;
import androidx.hilt.work.WorkerAssistedFactory;
import androidx.hilt.work.WorkerFactoryModule_ProvideFactoryFactory;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;
import com.beoffline.app.background.BackgroundProtectionManager;
import com.beoffline.app.data.local.BeOfflineDatabase;
import com.beoffline.app.data.local.BlockRuleDao;
import com.beoffline.app.data.repository.BlockRuleRepository;
import com.beoffline.app.di.AppModule_ProvideBlockRuleDaoFactory;
import com.beoffline.app.di.AppModule_ProvideDatabaseFactory;
import com.beoffline.app.notifications.BlockedAppNotificationListenerService;
import com.beoffline.app.notifications.BlockedAppNotificationListenerService_MembersInjector;
import com.beoffline.app.receiver.BootReceiver;
import com.beoffline.app.receiver.BootReceiver_MembersInjector;
import com.beoffline.app.receiver.ScheduleAlarmReceiver;
import com.beoffline.app.receiver.ScheduleAlarmReceiver_MembersInjector;
import com.beoffline.app.receiver.VpnRecoveryReceiver;
import com.beoffline.app.receiver.VpnRecoveryReceiver_MembersInjector;
import com.beoffline.app.scheduler.StartRuleWorker;
import com.beoffline.app.scheduler.StartRuleWorker_AssistedFactory;
import com.beoffline.app.scheduler.StopRuleWorker;
import com.beoffline.app.scheduler.StopRuleWorker_AssistedFactory;
import com.beoffline.app.support.BlockedTrafficAlertManager;
import com.beoffline.app.support.IssueReporter;
import com.beoffline.app.ui.screens.AppPickerViewModel;
import com.beoffline.app.ui.screens.AppPickerViewModel_HiltModules;
import com.beoffline.app.ui.screens.DashboardViewModel;
import com.beoffline.app.ui.screens.DashboardViewModel_HiltModules;
import com.beoffline.app.ui.screens.RuleCreatorViewModel;
import com.beoffline.app.ui.screens.RuleCreatorViewModel_HiltModules;
import com.beoffline.app.vpn.BeOfflineVpnService;
import com.beoffline.app.vpn.BeOfflineVpnService_MembersInjector;
import com.beoffline.app.vpn.VpnController;
import com.beoffline.app.vpn.VpnHealthWorker;
import com.beoffline.app.vpn.VpnHealthWorker_AssistedFactory;
import com.beoffline.app.vpn.VpnStateManager;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import dagger.hilt.android.ActivityRetainedLifecycle;
import dagger.hilt.android.ViewModelLifecycle;
import dagger.hilt.android.internal.builders.ActivityComponentBuilder;
import dagger.hilt.android.internal.builders.ActivityRetainedComponentBuilder;
import dagger.hilt.android.internal.builders.FragmentComponentBuilder;
import dagger.hilt.android.internal.builders.ServiceComponentBuilder;
import dagger.hilt.android.internal.builders.ViewComponentBuilder;
import dagger.hilt.android.internal.builders.ViewModelComponentBuilder;
import dagger.hilt.android.internal.builders.ViewWithFragmentComponentBuilder;
import dagger.hilt.android.internal.lifecycle.DefaultViewModelFactories;
import dagger.hilt.android.internal.lifecycle.DefaultViewModelFactories_InternalFactoryFactory_Factory;
import dagger.hilt.android.internal.managers.ActivityRetainedComponentManager_LifecycleModule_ProvideActivityRetainedLifecycleFactory;
import dagger.hilt.android.internal.managers.SavedStateHandleHolder;
import dagger.hilt.android.internal.modules.ApplicationContextModule;
import dagger.hilt.android.internal.modules.ApplicationContextModule_ProvideContextFactory;
import dagger.internal.DaggerGenerated;
import dagger.internal.DoubleCheck;
import dagger.internal.IdentifierNameString;
import dagger.internal.KeepFieldType;
import dagger.internal.LazyClassKeyMap;
import dagger.internal.MapBuilder;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import dagger.internal.SingleCheck;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

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
public final class DaggerBeOfflineApp_HiltComponents_SingletonC {
  private DaggerBeOfflineApp_HiltComponents_SingletonC() {
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private ApplicationContextModule applicationContextModule;

    private Builder() {
    }

    public Builder applicationContextModule(ApplicationContextModule applicationContextModule) {
      this.applicationContextModule = Preconditions.checkNotNull(applicationContextModule);
      return this;
    }

    public BeOfflineApp_HiltComponents.SingletonC build() {
      Preconditions.checkBuilderRequirement(applicationContextModule, ApplicationContextModule.class);
      return new SingletonCImpl(applicationContextModule);
    }
  }

  private static final class ActivityRetainedCBuilder implements BeOfflineApp_HiltComponents.ActivityRetainedC.Builder {
    private final SingletonCImpl singletonCImpl;

    private SavedStateHandleHolder savedStateHandleHolder;

    private ActivityRetainedCBuilder(SingletonCImpl singletonCImpl) {
      this.singletonCImpl = singletonCImpl;
    }

    @Override
    public ActivityRetainedCBuilder savedStateHandleHolder(
        SavedStateHandleHolder savedStateHandleHolder) {
      this.savedStateHandleHolder = Preconditions.checkNotNull(savedStateHandleHolder);
      return this;
    }

    @Override
    public BeOfflineApp_HiltComponents.ActivityRetainedC build() {
      Preconditions.checkBuilderRequirement(savedStateHandleHolder, SavedStateHandleHolder.class);
      return new ActivityRetainedCImpl(singletonCImpl, savedStateHandleHolder);
    }
  }

  private static final class ActivityCBuilder implements BeOfflineApp_HiltComponents.ActivityC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private Activity activity;

    private ActivityCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
    }

    @Override
    public ActivityCBuilder activity(Activity activity) {
      this.activity = Preconditions.checkNotNull(activity);
      return this;
    }

    @Override
    public BeOfflineApp_HiltComponents.ActivityC build() {
      Preconditions.checkBuilderRequirement(activity, Activity.class);
      return new ActivityCImpl(singletonCImpl, activityRetainedCImpl, activity);
    }
  }

  private static final class FragmentCBuilder implements BeOfflineApp_HiltComponents.FragmentC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private Fragment fragment;

    private FragmentCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
    }

    @Override
    public FragmentCBuilder fragment(Fragment fragment) {
      this.fragment = Preconditions.checkNotNull(fragment);
      return this;
    }

    @Override
    public BeOfflineApp_HiltComponents.FragmentC build() {
      Preconditions.checkBuilderRequirement(fragment, Fragment.class);
      return new FragmentCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, fragment);
    }
  }

  private static final class ViewWithFragmentCBuilder implements BeOfflineApp_HiltComponents.ViewWithFragmentC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl;

    private View view;

    private ViewWithFragmentCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl,
        FragmentCImpl fragmentCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
      this.fragmentCImpl = fragmentCImpl;
    }

    @Override
    public ViewWithFragmentCBuilder view(View view) {
      this.view = Preconditions.checkNotNull(view);
      return this;
    }

    @Override
    public BeOfflineApp_HiltComponents.ViewWithFragmentC build() {
      Preconditions.checkBuilderRequirement(view, View.class);
      return new ViewWithFragmentCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, fragmentCImpl, view);
    }
  }

  private static final class ViewCBuilder implements BeOfflineApp_HiltComponents.ViewC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private View view;

    private ViewCBuilder(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        ActivityCImpl activityCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
    }

    @Override
    public ViewCBuilder view(View view) {
      this.view = Preconditions.checkNotNull(view);
      return this;
    }

    @Override
    public BeOfflineApp_HiltComponents.ViewC build() {
      Preconditions.checkBuilderRequirement(view, View.class);
      return new ViewCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, view);
    }
  }

  private static final class ViewModelCBuilder implements BeOfflineApp_HiltComponents.ViewModelC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private SavedStateHandle savedStateHandle;

    private ViewModelLifecycle viewModelLifecycle;

    private ViewModelCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
    }

    @Override
    public ViewModelCBuilder savedStateHandle(SavedStateHandle handle) {
      this.savedStateHandle = Preconditions.checkNotNull(handle);
      return this;
    }

    @Override
    public ViewModelCBuilder viewModelLifecycle(ViewModelLifecycle viewModelLifecycle) {
      this.viewModelLifecycle = Preconditions.checkNotNull(viewModelLifecycle);
      return this;
    }

    @Override
    public BeOfflineApp_HiltComponents.ViewModelC build() {
      Preconditions.checkBuilderRequirement(savedStateHandle, SavedStateHandle.class);
      Preconditions.checkBuilderRequirement(viewModelLifecycle, ViewModelLifecycle.class);
      return new ViewModelCImpl(singletonCImpl, activityRetainedCImpl, savedStateHandle, viewModelLifecycle);
    }
  }

  private static final class ServiceCBuilder implements BeOfflineApp_HiltComponents.ServiceC.Builder {
    private final SingletonCImpl singletonCImpl;

    private Service service;

    private ServiceCBuilder(SingletonCImpl singletonCImpl) {
      this.singletonCImpl = singletonCImpl;
    }

    @Override
    public ServiceCBuilder service(Service service) {
      this.service = Preconditions.checkNotNull(service);
      return this;
    }

    @Override
    public BeOfflineApp_HiltComponents.ServiceC build() {
      Preconditions.checkBuilderRequirement(service, Service.class);
      return new ServiceCImpl(singletonCImpl, service);
    }
  }

  private static final class ViewWithFragmentCImpl extends BeOfflineApp_HiltComponents.ViewWithFragmentC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl;

    private final ViewWithFragmentCImpl viewWithFragmentCImpl = this;

    private ViewWithFragmentCImpl(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl,
        FragmentCImpl fragmentCImpl, View viewParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
      this.fragmentCImpl = fragmentCImpl;


    }
  }

  private static final class FragmentCImpl extends BeOfflineApp_HiltComponents.FragmentC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl = this;

    private FragmentCImpl(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl,
        Fragment fragmentParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;


    }

    @Override
    public DefaultViewModelFactories.InternalFactoryFactory getHiltInternalFactoryFactory() {
      return activityCImpl.getHiltInternalFactoryFactory();
    }

    @Override
    public ViewWithFragmentComponentBuilder viewWithFragmentComponentBuilder() {
      return new ViewWithFragmentCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl, fragmentCImpl);
    }
  }

  private static final class ViewCImpl extends BeOfflineApp_HiltComponents.ViewC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final ViewCImpl viewCImpl = this;

    private ViewCImpl(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        ActivityCImpl activityCImpl, View viewParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;


    }
  }

  private static final class ActivityCImpl extends BeOfflineApp_HiltComponents.ActivityC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl = this;

    private ActivityCImpl(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, Activity activityParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;


    }

    @Override
    public void injectMainActivity(MainActivity mainActivity) {
      injectMainActivity2(mainActivity);
    }

    @Override
    public DefaultViewModelFactories.InternalFactoryFactory getHiltInternalFactoryFactory() {
      return DefaultViewModelFactories_InternalFactoryFactory_Factory.newInstance(getViewModelKeys(), new ViewModelCBuilder(singletonCImpl, activityRetainedCImpl));
    }

    @Override
    public Map<Class<?>, Boolean> getViewModelKeys() {
      return LazyClassKeyMap.<Boolean>of(MapBuilder.<String, Boolean>newMapBuilder(3).put(LazyClassKeyProvider.com_beoffline_app_ui_screens_AppPickerViewModel, AppPickerViewModel_HiltModules.KeyModule.provide()).put(LazyClassKeyProvider.com_beoffline_app_ui_screens_DashboardViewModel, DashboardViewModel_HiltModules.KeyModule.provide()).put(LazyClassKeyProvider.com_beoffline_app_ui_screens_RuleCreatorViewModel, RuleCreatorViewModel_HiltModules.KeyModule.provide()).build());
    }

    @Override
    public ViewModelComponentBuilder getViewModelComponentBuilder() {
      return new ViewModelCBuilder(singletonCImpl, activityRetainedCImpl);
    }

    @Override
    public FragmentComponentBuilder fragmentComponentBuilder() {
      return new FragmentCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl);
    }

    @Override
    public ViewComponentBuilder viewComponentBuilder() {
      return new ViewCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl);
    }

    @CanIgnoreReturnValue
    private MainActivity injectMainActivity2(MainActivity instance) {
      MainActivity_MembersInjector.injectVpnController(instance, singletonCImpl.vpnControllerProvider.get());
      return instance;
    }

    @IdentifierNameString
    private static final class LazyClassKeyProvider {
      static String com_beoffline_app_ui_screens_RuleCreatorViewModel = "com.beoffline.app.ui.screens.RuleCreatorViewModel";

      static String com_beoffline_app_ui_screens_DashboardViewModel = "com.beoffline.app.ui.screens.DashboardViewModel";

      static String com_beoffline_app_ui_screens_AppPickerViewModel = "com.beoffline.app.ui.screens.AppPickerViewModel";

      @KeepFieldType
      RuleCreatorViewModel com_beoffline_app_ui_screens_RuleCreatorViewModel2;

      @KeepFieldType
      DashboardViewModel com_beoffline_app_ui_screens_DashboardViewModel2;

      @KeepFieldType
      AppPickerViewModel com_beoffline_app_ui_screens_AppPickerViewModel2;
    }
  }

  private static final class ViewModelCImpl extends BeOfflineApp_HiltComponents.ViewModelC {
    private final SavedStateHandle savedStateHandle;

    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ViewModelCImpl viewModelCImpl = this;

    private Provider<AppPickerViewModel> appPickerViewModelProvider;

    private Provider<DashboardViewModel> dashboardViewModelProvider;

    private Provider<RuleCreatorViewModel> ruleCreatorViewModelProvider;

    private ViewModelCImpl(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, SavedStateHandle savedStateHandleParam,
        ViewModelLifecycle viewModelLifecycleParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.savedStateHandle = savedStateHandleParam;
      initialize(savedStateHandleParam, viewModelLifecycleParam);

    }

    @SuppressWarnings("unchecked")
    private void initialize(final SavedStateHandle savedStateHandleParam,
        final ViewModelLifecycle viewModelLifecycleParam) {
      this.appPickerViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 0);
      this.dashboardViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 1);
      this.ruleCreatorViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 2);
    }

    @Override
    public Map<Class<?>, javax.inject.Provider<ViewModel>> getHiltViewModelMap() {
      return LazyClassKeyMap.<javax.inject.Provider<ViewModel>>of(MapBuilder.<String, javax.inject.Provider<ViewModel>>newMapBuilder(3).put(LazyClassKeyProvider.com_beoffline_app_ui_screens_AppPickerViewModel, ((Provider) appPickerViewModelProvider)).put(LazyClassKeyProvider.com_beoffline_app_ui_screens_DashboardViewModel, ((Provider) dashboardViewModelProvider)).put(LazyClassKeyProvider.com_beoffline_app_ui_screens_RuleCreatorViewModel, ((Provider) ruleCreatorViewModelProvider)).build());
    }

    @Override
    public Map<Class<?>, Object> getHiltViewModelAssistedMap() {
      return Collections.<Class<?>, Object>emptyMap();
    }

    @IdentifierNameString
    private static final class LazyClassKeyProvider {
      static String com_beoffline_app_ui_screens_RuleCreatorViewModel = "com.beoffline.app.ui.screens.RuleCreatorViewModel";

      static String com_beoffline_app_ui_screens_DashboardViewModel = "com.beoffline.app.ui.screens.DashboardViewModel";

      static String com_beoffline_app_ui_screens_AppPickerViewModel = "com.beoffline.app.ui.screens.AppPickerViewModel";

      @KeepFieldType
      RuleCreatorViewModel com_beoffline_app_ui_screens_RuleCreatorViewModel2;

      @KeepFieldType
      DashboardViewModel com_beoffline_app_ui_screens_DashboardViewModel2;

      @KeepFieldType
      AppPickerViewModel com_beoffline_app_ui_screens_AppPickerViewModel2;
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final ActivityRetainedCImpl activityRetainedCImpl;

      private final ViewModelCImpl viewModelCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
          ViewModelCImpl viewModelCImpl, int id) {
        this.singletonCImpl = singletonCImpl;
        this.activityRetainedCImpl = activityRetainedCImpl;
        this.viewModelCImpl = viewModelCImpl;
        this.id = id;
      }

      @SuppressWarnings("unchecked")
      @Override
      public T get() {
        switch (id) {
          case 0: // com.beoffline.app.ui.screens.AppPickerViewModel 
          return (T) new AppPickerViewModel(singletonCImpl.blockRuleRepositoryProvider.get());

          case 1: // com.beoffline.app.ui.screens.DashboardViewModel 
          return (T) new DashboardViewModel(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule), singletonCImpl.backgroundProtectionManagerProvider.get(), singletonCImpl.blockRuleRepositoryProvider.get(), singletonCImpl.blockedTrafficAlertManagerProvider.get(), singletonCImpl.issueReporterProvider.get(), singletonCImpl.vpnControllerProvider.get(), singletonCImpl.vpnStateManagerProvider.get());

          case 2: // com.beoffline.app.ui.screens.RuleCreatorViewModel 
          return (T) new RuleCreatorViewModel(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule), singletonCImpl.blockRuleRepositoryProvider.get(), viewModelCImpl.savedStateHandle);

          default: throw new AssertionError(id);
        }
      }
    }
  }

  private static final class ActivityRetainedCImpl extends BeOfflineApp_HiltComponents.ActivityRetainedC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl = this;

    private Provider<ActivityRetainedLifecycle> provideActivityRetainedLifecycleProvider;

    private ActivityRetainedCImpl(SingletonCImpl singletonCImpl,
        SavedStateHandleHolder savedStateHandleHolderParam) {
      this.singletonCImpl = singletonCImpl;

      initialize(savedStateHandleHolderParam);

    }

    @SuppressWarnings("unchecked")
    private void initialize(final SavedStateHandleHolder savedStateHandleHolderParam) {
      this.provideActivityRetainedLifecycleProvider = DoubleCheck.provider(new SwitchingProvider<ActivityRetainedLifecycle>(singletonCImpl, activityRetainedCImpl, 0));
    }

    @Override
    public ActivityComponentBuilder activityComponentBuilder() {
      return new ActivityCBuilder(singletonCImpl, activityRetainedCImpl);
    }

    @Override
    public ActivityRetainedLifecycle getActivityRetainedLifecycle() {
      return provideActivityRetainedLifecycleProvider.get();
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final ActivityRetainedCImpl activityRetainedCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
          int id) {
        this.singletonCImpl = singletonCImpl;
        this.activityRetainedCImpl = activityRetainedCImpl;
        this.id = id;
      }

      @SuppressWarnings("unchecked")
      @Override
      public T get() {
        switch (id) {
          case 0: // dagger.hilt.android.ActivityRetainedLifecycle 
          return (T) ActivityRetainedComponentManager_LifecycleModule_ProvideActivityRetainedLifecycleFactory.provideActivityRetainedLifecycle();

          default: throw new AssertionError(id);
        }
      }
    }
  }

  private static final class ServiceCImpl extends BeOfflineApp_HiltComponents.ServiceC {
    private final SingletonCImpl singletonCImpl;

    private final ServiceCImpl serviceCImpl = this;

    private ServiceCImpl(SingletonCImpl singletonCImpl, Service serviceParam) {
      this.singletonCImpl = singletonCImpl;


    }

    @Override
    public void injectBlockedAppNotificationListenerService(
        BlockedAppNotificationListenerService blockedAppNotificationListenerService) {
      injectBlockedAppNotificationListenerService2(blockedAppNotificationListenerService);
    }

    @Override
    public void injectBeOfflineVpnService(BeOfflineVpnService beOfflineVpnService) {
      injectBeOfflineVpnService2(beOfflineVpnService);
    }

    @CanIgnoreReturnValue
    private BlockedAppNotificationListenerService injectBlockedAppNotificationListenerService2(
        BlockedAppNotificationListenerService instance) {
      BlockedAppNotificationListenerService_MembersInjector.injectRepository(instance, singletonCImpl.blockRuleRepositoryProvider.get());
      return instance;
    }

    @CanIgnoreReturnValue
    private BeOfflineVpnService injectBeOfflineVpnService2(BeOfflineVpnService instance2) {
      BeOfflineVpnService_MembersInjector.injectVpnStateManager(instance2, singletonCImpl.vpnStateManagerProvider.get());
      BeOfflineVpnService_MembersInjector.injectRepository(instance2, singletonCImpl.blockRuleRepositoryProvider.get());
      BeOfflineVpnService_MembersInjector.injectBlockedTrafficAlertManager(instance2, singletonCImpl.blockedTrafficAlertManagerProvider.get());
      return instance2;
    }
  }

  private static final class SingletonCImpl extends BeOfflineApp_HiltComponents.SingletonC {
    private final ApplicationContextModule applicationContextModule;

    private final SingletonCImpl singletonCImpl = this;

    private Provider<BeOfflineDatabase> provideDatabaseProvider;

    private Provider<BlockRuleDao> provideBlockRuleDaoProvider;

    private Provider<BlockRuleRepository> blockRuleRepositoryProvider;

    private Provider<VpnStateManager> vpnStateManagerProvider;

    private Provider<VpnController> vpnControllerProvider;

    private Provider<StartRuleWorker_AssistedFactory> startRuleWorker_AssistedFactoryProvider;

    private Provider<StopRuleWorker_AssistedFactory> stopRuleWorker_AssistedFactoryProvider;

    private Provider<VpnHealthWorker_AssistedFactory> vpnHealthWorker_AssistedFactoryProvider;

    private Provider<BackgroundProtectionManager> backgroundProtectionManagerProvider;

    private Provider<BlockedTrafficAlertManager> blockedTrafficAlertManagerProvider;

    private Provider<IssueReporter> issueReporterProvider;

    private SingletonCImpl(ApplicationContextModule applicationContextModuleParam) {
      this.applicationContextModule = applicationContextModuleParam;
      initialize(applicationContextModuleParam);

    }

    private Map<String, javax.inject.Provider<WorkerAssistedFactory<? extends ListenableWorker>>> mapOfStringAndProviderOfWorkerAssistedFactoryOf(
        ) {
      return MapBuilder.<String, javax.inject.Provider<WorkerAssistedFactory<? extends ListenableWorker>>>newMapBuilder(3).put("com.beoffline.app.scheduler.StartRuleWorker", ((Provider) startRuleWorker_AssistedFactoryProvider)).put("com.beoffline.app.scheduler.StopRuleWorker", ((Provider) stopRuleWorker_AssistedFactoryProvider)).put("com.beoffline.app.vpn.VpnHealthWorker", ((Provider) vpnHealthWorker_AssistedFactoryProvider)).build();
    }

    private HiltWorkerFactory hiltWorkerFactory() {
      return WorkerFactoryModule_ProvideFactoryFactory.provideFactory(mapOfStringAndProviderOfWorkerAssistedFactoryOf());
    }

    @SuppressWarnings("unchecked")
    private void initialize(final ApplicationContextModule applicationContextModuleParam) {
      this.provideDatabaseProvider = DoubleCheck.provider(new SwitchingProvider<BeOfflineDatabase>(singletonCImpl, 3));
      this.provideBlockRuleDaoProvider = DoubleCheck.provider(new SwitchingProvider<BlockRuleDao>(singletonCImpl, 2));
      this.blockRuleRepositoryProvider = DoubleCheck.provider(new SwitchingProvider<BlockRuleRepository>(singletonCImpl, 1));
      this.vpnStateManagerProvider = DoubleCheck.provider(new SwitchingProvider<VpnStateManager>(singletonCImpl, 5));
      this.vpnControllerProvider = DoubleCheck.provider(new SwitchingProvider<VpnController>(singletonCImpl, 4));
      this.startRuleWorker_AssistedFactoryProvider = SingleCheck.provider(new SwitchingProvider<StartRuleWorker_AssistedFactory>(singletonCImpl, 0));
      this.stopRuleWorker_AssistedFactoryProvider = SingleCheck.provider(new SwitchingProvider<StopRuleWorker_AssistedFactory>(singletonCImpl, 6));
      this.vpnHealthWorker_AssistedFactoryProvider = SingleCheck.provider(new SwitchingProvider<VpnHealthWorker_AssistedFactory>(singletonCImpl, 7));
      this.backgroundProtectionManagerProvider = DoubleCheck.provider(new SwitchingProvider<BackgroundProtectionManager>(singletonCImpl, 8));
      this.blockedTrafficAlertManagerProvider = DoubleCheck.provider(new SwitchingProvider<BlockedTrafficAlertManager>(singletonCImpl, 9));
      this.issueReporterProvider = DoubleCheck.provider(new SwitchingProvider<IssueReporter>(singletonCImpl, 10));
    }

    @Override
    public void injectBeOfflineApp(BeOfflineApp beOfflineApp) {
      injectBeOfflineApp2(beOfflineApp);
    }

    @Override
    public void injectBootReceiver(BootReceiver bootReceiver) {
      injectBootReceiver2(bootReceiver);
    }

    @Override
    public void injectScheduleAlarmReceiver(ScheduleAlarmReceiver scheduleAlarmReceiver) {
      injectScheduleAlarmReceiver2(scheduleAlarmReceiver);
    }

    @Override
    public void injectVpnRecoveryReceiver(VpnRecoveryReceiver vpnRecoveryReceiver) {
      injectVpnRecoveryReceiver2(vpnRecoveryReceiver);
    }

    @Override
    public Set<Boolean> getDisableFragmentGetContextFix() {
      return Collections.<Boolean>emptySet();
    }

    @Override
    public ActivityRetainedComponentBuilder retainedComponentBuilder() {
      return new ActivityRetainedCBuilder(singletonCImpl);
    }

    @Override
    public ServiceComponentBuilder serviceComponentBuilder() {
      return new ServiceCBuilder(singletonCImpl);
    }

    @CanIgnoreReturnValue
    private BeOfflineApp injectBeOfflineApp2(BeOfflineApp instance) {
      BeOfflineApp_MembersInjector.injectWorkerFactory(instance, hiltWorkerFactory());
      return instance;
    }

    @CanIgnoreReturnValue
    private BootReceiver injectBootReceiver2(BootReceiver instance2) {
      BootReceiver_MembersInjector.injectRepository(instance2, blockRuleRepositoryProvider.get());
      BootReceiver_MembersInjector.injectVpnController(instance2, vpnControllerProvider.get());
      return instance2;
    }

    @CanIgnoreReturnValue
    private ScheduleAlarmReceiver injectScheduleAlarmReceiver2(ScheduleAlarmReceiver instance3) {
      ScheduleAlarmReceiver_MembersInjector.injectRepository(instance3, blockRuleRepositoryProvider.get());
      ScheduleAlarmReceiver_MembersInjector.injectVpnController(instance3, vpnControllerProvider.get());
      return instance3;
    }

    @CanIgnoreReturnValue
    private VpnRecoveryReceiver injectVpnRecoveryReceiver2(VpnRecoveryReceiver instance4) {
      VpnRecoveryReceiver_MembersInjector.injectRepository(instance4, blockRuleRepositoryProvider.get());
      VpnRecoveryReceiver_MembersInjector.injectVpnController(instance4, vpnControllerProvider.get());
      return instance4;
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, int id) {
        this.singletonCImpl = singletonCImpl;
        this.id = id;
      }

      @SuppressWarnings("unchecked")
      @Override
      public T get() {
        switch (id) {
          case 0: // com.beoffline.app.scheduler.StartRuleWorker_AssistedFactory 
          return (T) new StartRuleWorker_AssistedFactory() {
            @Override
            public StartRuleWorker create(Context context, WorkerParameters params) {
              return new StartRuleWorker(context, params, singletonCImpl.blockRuleRepositoryProvider.get(), singletonCImpl.vpnControllerProvider.get());
            }
          };

          case 1: // com.beoffline.app.data.repository.BlockRuleRepository 
          return (T) new BlockRuleRepository(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule), singletonCImpl.provideBlockRuleDaoProvider.get());

          case 2: // com.beoffline.app.data.local.BlockRuleDao 
          return (T) AppModule_ProvideBlockRuleDaoFactory.provideBlockRuleDao(singletonCImpl.provideDatabaseProvider.get());

          case 3: // com.beoffline.app.data.local.BeOfflineDatabase 
          return (T) AppModule_ProvideDatabaseFactory.provideDatabase(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 4: // com.beoffline.app.vpn.VpnController 
          return (T) new VpnController(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule), singletonCImpl.vpnStateManagerProvider.get());

          case 5: // com.beoffline.app.vpn.VpnStateManager 
          return (T) new VpnStateManager();

          case 6: // com.beoffline.app.scheduler.StopRuleWorker_AssistedFactory 
          return (T) new StopRuleWorker_AssistedFactory() {
            @Override
            public StopRuleWorker create(Context context2, WorkerParameters params2) {
              return new StopRuleWorker(context2, params2, singletonCImpl.blockRuleRepositoryProvider.get(), singletonCImpl.vpnControllerProvider.get());
            }
          };

          case 7: // com.beoffline.app.vpn.VpnHealthWorker_AssistedFactory 
          return (T) new VpnHealthWorker_AssistedFactory() {
            @Override
            public VpnHealthWorker create(Context context3, WorkerParameters params3) {
              return new VpnHealthWorker(context3, params3, singletonCImpl.blockRuleRepositoryProvider.get(), singletonCImpl.vpnControllerProvider.get(), singletonCImpl.vpnStateManagerProvider.get());
            }
          };

          case 8: // com.beoffline.app.background.BackgroundProtectionManager 
          return (T) new BackgroundProtectionManager(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 9: // com.beoffline.app.support.BlockedTrafficAlertManager 
          return (T) new BlockedTrafficAlertManager(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 10: // com.beoffline.app.support.IssueReporter 
          return (T) new IssueReporter();

          default: throw new AssertionError(id);
        }
      }
    }
  }
}
