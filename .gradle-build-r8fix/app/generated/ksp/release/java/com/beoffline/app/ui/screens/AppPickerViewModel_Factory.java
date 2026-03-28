package com.beoffline.app.ui.screens;

import com.beoffline.app.data.repository.BlockRuleRepository;
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
public final class AppPickerViewModel_Factory implements Factory<AppPickerViewModel> {
  private final Provider<BlockRuleRepository> repositoryProvider;

  public AppPickerViewModel_Factory(Provider<BlockRuleRepository> repositoryProvider) {
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public AppPickerViewModel get() {
    return newInstance(repositoryProvider.get());
  }

  public static AppPickerViewModel_Factory create(
      Provider<BlockRuleRepository> repositoryProvider) {
    return new AppPickerViewModel_Factory(repositoryProvider);
  }

  public static AppPickerViewModel newInstance(BlockRuleRepository repository) {
    return new AppPickerViewModel(repository);
  }
}
