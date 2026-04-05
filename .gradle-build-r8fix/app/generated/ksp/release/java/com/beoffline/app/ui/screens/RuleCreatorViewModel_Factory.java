package com.beoffline.app.ui.screens;

import android.content.Context;
import androidx.lifecycle.SavedStateHandle;
import com.beoffline.app.data.repository.BlockRuleRepository;
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
public final class RuleCreatorViewModel_Factory implements Factory<RuleCreatorViewModel> {
  private final Provider<Context> contextProvider;

  private final Provider<BlockRuleRepository> repositoryProvider;

  private final Provider<SavedStateHandle> savedStateHandleProvider;

  public RuleCreatorViewModel_Factory(Provider<Context> contextProvider,
      Provider<BlockRuleRepository> repositoryProvider,
      Provider<SavedStateHandle> savedStateHandleProvider) {
    this.contextProvider = contextProvider;
    this.repositoryProvider = repositoryProvider;
    this.savedStateHandleProvider = savedStateHandleProvider;
  }

  @Override
  public RuleCreatorViewModel get() {
    return newInstance(contextProvider.get(), repositoryProvider.get(), savedStateHandleProvider.get());
  }

  public static RuleCreatorViewModel_Factory create(Provider<Context> contextProvider,
      Provider<BlockRuleRepository> repositoryProvider,
      Provider<SavedStateHandle> savedStateHandleProvider) {
    return new RuleCreatorViewModel_Factory(contextProvider, repositoryProvider, savedStateHandleProvider);
  }

  public static RuleCreatorViewModel newInstance(Context context, BlockRuleRepository repository,
      SavedStateHandle savedStateHandle) {
    return new RuleCreatorViewModel(context, repository, savedStateHandle);
  }
}
