package com.beoffline.app.support;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

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
public final class IssueReporter_Factory implements Factory<IssueReporter> {
  @Override
  public IssueReporter get() {
    return newInstance();
  }

  public static IssueReporter_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static IssueReporter newInstance() {
    return new IssueReporter();
  }

  private static final class InstanceHolder {
    private static final IssueReporter_Factory INSTANCE = new IssueReporter_Factory();
  }
}
