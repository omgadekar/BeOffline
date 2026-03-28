package com.beoffline.app.di;

import com.beoffline.app.data.local.BeOfflineDatabase;
import com.beoffline.app.data.local.BlockRuleDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
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
public final class AppModule_ProvideBlockRuleDaoFactory implements Factory<BlockRuleDao> {
  private final Provider<BeOfflineDatabase> dbProvider;

  public AppModule_ProvideBlockRuleDaoFactory(Provider<BeOfflineDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public BlockRuleDao get() {
    return provideBlockRuleDao(dbProvider.get());
  }

  public static AppModule_ProvideBlockRuleDaoFactory create(
      Provider<BeOfflineDatabase> dbProvider) {
    return new AppModule_ProvideBlockRuleDaoFactory(dbProvider);
  }

  public static BlockRuleDao provideBlockRuleDao(BeOfflineDatabase db) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideBlockRuleDao(db));
  }
}
