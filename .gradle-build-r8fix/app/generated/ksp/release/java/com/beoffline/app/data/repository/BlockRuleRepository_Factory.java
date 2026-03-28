package com.beoffline.app.data.repository;

import android.content.Context;
import com.beoffline.app.data.local.BlockRuleDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
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
public final class BlockRuleRepository_Factory implements Factory<BlockRuleRepository> {
  private final Provider<Context> contextProvider;

  private final Provider<BlockRuleDao> daoProvider;

  public BlockRuleRepository_Factory(Provider<Context> contextProvider,
      Provider<BlockRuleDao> daoProvider) {
    this.contextProvider = contextProvider;
    this.daoProvider = daoProvider;
  }

  @Override
  public BlockRuleRepository get() {
    return newInstance(contextProvider.get(), daoProvider.get());
  }

  public static BlockRuleRepository_Factory create(Provider<Context> contextProvider,
      Provider<BlockRuleDao> daoProvider) {
    return new BlockRuleRepository_Factory(contextProvider, daoProvider);
  }

  public static BlockRuleRepository newInstance(Context context, BlockRuleDao dao) {
    return new BlockRuleRepository(context, dao);
  }
}
