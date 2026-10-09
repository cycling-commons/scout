# Scout Karoo release shrinking.

# karoo-ext talks to the Karoo over AIDL with serialized models. Its own keep rule
# lives in the library's proguard-rules.pro, which is not shipped as a consumer
# rule (and the composite build wouldn't apply it anyway), so repeat it here.
-keep class io.hammerhead.karooext.** { *; }

# Readable crash traces: keep line numbers, decode names with mapping.txt.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
