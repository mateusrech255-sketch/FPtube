package br.com.fpagency.fptube.engine;

import com.yausername.youtubedl_android.YoutubeDL;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
    "deprecation",
    "nullness:initialization.field.uninitialized"
})
public final class EngineModule_ProvideYoutubeDLFactory implements Factory<YoutubeDL> {
  @Override
  public YoutubeDL get() {
    return provideYoutubeDL();
  }

  public static EngineModule_ProvideYoutubeDLFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static YoutubeDL provideYoutubeDL() {
    return Preconditions.checkNotNullFromProvides(EngineModule.INSTANCE.provideYoutubeDL());
  }

  private static final class InstanceHolder {
    static final EngineModule_ProvideYoutubeDLFactory INSTANCE = new EngineModule_ProvideYoutubeDLFactory();
  }
}
