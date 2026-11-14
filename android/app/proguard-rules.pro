# JNI methods are resolved by their fully-qualified generated names.
-keep class expo.modules.localbotapi.LocalBotApiModule { *; }

# These components are instantiated by Android from the manifest.
-keep class space.manus.tgvidapp.t145521605804137.MainActivity { *; }
-keep class space.manus.tgvidapp.t145521605804137.DownloadsActivity { *; }
-keep class space.manus.tgvidapp.t145521605804137.SettingsActivity { *; }
-keep class space.manus.tgvidapp.t145521605804137.LocalBotApiForegroundService { *; }
