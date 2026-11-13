import { useCallback, useEffect, useState } from "react";
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from "react-native";
import { useFocusEffect, useRouter } from "expo-router";

import { addBotApiProgressListener, downloadWithLocalBotApi, getLocalBotApiStatus, isLocalBotApiAvailable, resolveWithTelethon, startLocalBotApi, type BotApiDownloadProgress } from "@/lib/local-bot-api";
import { loadTelegramCredentials, type SavedTelegramCredentials } from "@/lib/secure-config";

export default function HomeScreen() {
  const router = useRouter();
  const [link, setLink] = useState("");
  const [credentials, setCredentials] = useState<SavedTelegramCredentials | null>(null);
  const [serverReady, setServerReady] = useState(false);
  const [busy, setBusy] = useState(false);
  const [stage, setStage] = useState("");
  const [errorStage, setErrorStage] = useState("");
  const [error, setError] = useState("");
  const [savedUri, setSavedUri] = useState("");
  const [progress, setProgress] = useState<BotApiDownloadProgress | null>(null);

  const refreshState = useCallback(async () => {
    const [saved, status] = await Promise.all([loadTelegramCredentials().catch(() => null), getLocalBotApiStatus().catch(() => ({ running: false }))]);
    setCredentials(saved);
    setServerReady(Boolean(status.running));
  }, []);

  useFocusEffect(useCallback(() => {
    void refreshState();
  }, [refreshState]));

  useEffect(() => {
    const subscription = addBotApiProgressListener((value) => setProgress(value));
    return () => subscription.remove();
  }, []);

  const runDownload = async () => {
    setError("");
    setErrorStage("");
    setSavedUri("");
    setProgress(null);

    if (!credentials) {
      router.push("/settings");
      return;
    }
    if (!link.trim()) {
      setError("Telegram video link ကို ထည့်ပါ။");
      return;
    }
    if (!isLocalBotApiAvailable) {
      setError("ဒီ Android build မှာ app အတွင်း Bot API Server native module မပါသေးပါ။ Download မစတင်ခဲ့ပါ။");
      return;
    }

    setBusy(true);
    let activeStage = "စတင်နေပါတယ်";
    const setStep = (message: string) => {
      activeStage = message;
      setStage(message);
    };
    try {
      setStep("[1/3] App အတွင်း Bot API Server ကို စတင်နေပါတယ်…");
      await startLocalBotApi(Number(credentials.apiId), credentials.apiHash);
      setServerReady(true);

      setStep("[2/3] Telegram post အချက်အလက်ကို ရယူနေပါတယ်…");
      const video = await resolveWithTelethon(
        Number(credentials.apiId),
        credentials.apiHash,
        credentials.botToken,
        link,
      );

      if (!video.fileId) {
        throw new Error("Telethon က Local Bot API သုံးရန် file ID မရပါ။");
      }
      setStep("[3/3] Local Bot API Server မှ video ကို download လုပ်နေပါတယ်…");
      const result = await downloadWithLocalBotApi(
        credentials.botToken,
        video.fileId,
        video.fileName || `video_${video.messageId}.mp4`,
      );
      setSavedUri(result.uri);
      setStage("Download ပြီးပါပြီ");
      setProgress({ phase: "complete", copiedBytes: result.sizeBytes, totalBytes: result.sizeBytes });
    } catch (cause) {
      setStage("");
      const message = cause instanceof Error ? cause.message : "မသိသော error တစ်ခု ဖြစ်ပါတယ်။";
      setErrorStage(activeStage);
      setError(message);
    } finally {
      setBusy(false);
    }
  };

  const percent = progress && progress.totalBytes && progress.totalBytes > 0
    ? Math.min(100, Math.floor((progress.copiedBytes / progress.totalBytes) * 100))
    : null;

  return (
    <ScrollView style={styles.root} contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
      <View style={styles.topRow}>
        <View>
          <Text style={styles.eyebrow}>TELEGRAM VIDEO TOOL</Text>
          <Text style={styles.title}>Telegram ဗီဒီယို</Text>
        </View>
        <Pressable accessibilityRole="button" onPress={() => router.push("/settings")} style={styles.settingsButton}>
          <Text style={styles.settingsButtonText}>ဆက်တင်</Text>
        </Pressable>
      </View>

      <Text style={styles.intro}>အများမြင်နိုင်တဲ့ Telegram channel ဗီဒီယို link ကို ထည့်ပါ။</Text>

      <View style={styles.serverCard}>
        <View style={[styles.serverDot, serverReady && styles.serverDotReady]} />
        <View style={styles.serverCopy}>
          <Text style={styles.serverTitle}>App အတွင်း Bot API Server</Text>
          <Text style={styles.serverText}>
            {!isLocalBotApiAvailable
              ? "ဒီ build မှာ native server module မပါသေးပါ။ Download မရသေးပါ။"
              : serverReady ? "အလုပ်လုပ်နေပါတယ်" : "မစတင်ရသေးပါ"}
          </Text>
        </View>
        <Text style={[styles.serverBadge, serverReady && styles.serverBadgeReady]}>{serverReady ? "အဆင်သင့်" : "စောင့်နေပါတယ်"}</Text>
      </View>

      <View style={styles.inputCard}>
        <Text style={styles.label}>Telegram post link</Text>
        <TextInput
          accessibilityLabel="Telegram post link"
          value={link}
          onChangeText={setLink}
          autoCapitalize="none"
          autoCorrect={false}
          keyboardType="url"
          placeholder="https://t.me/channel/123"
          placeholderTextColor="#687780"
          style={styles.input}
        />
        <Text style={styles.hint}>အများမြင်နိုင်တဲ့ channel ရဲ့ video link ကိုသာ ထည့်ပါ။</Text>
      </View>

      <Pressable accessibilityRole="button" onPress={runDownload} disabled={busy} style={[styles.downloadButton, busy && styles.disabled]}>
        {busy ? <ActivityIndicator color="#071012" /> : <Text style={styles.downloadButtonText}>ဗီဒီယို ဒေါင်းလုဒ်လုပ်မည်</Text>}
      </Pressable>

      {!credentials ? (
        <Text style={styles.notice}>အရင်ဆုံး ဆက်တင်ထဲမှာ API ID, API Hash နဲ့ Bot Token ကို သိမ်းပါ။</Text>
      ) : null}

      {busy ? (
        <View style={styles.progressCard}>
          <View style={styles.progressHeader}>
            <ActivityIndicator color="#51d4c8" />
            <Text style={styles.progressStage}>{stage || "လုပ်ဆောင်နေပါတယ်…"}</Text>
          </View>
          {progress?.phase === "copying" && percent !== null ? (
            <>
              <View style={styles.track}><View style={[styles.fill, { width: `${percent}%` }]} /></View>
              <Text style={styles.progressNumbers}>
                {percent}% · {progress.copiedBytes.toLocaleString()} / {progress.totalBytes?.toLocaleString()} bytes
                {progress.speedBytesPerSecond && progress.speedBytesPerSecond > 0
                  ? ` · ${formatRate(progress.speedBytesPerSecond)}`
                  : ""}
              </Text>
            </>
          ) : (
            <Text style={styles.progressNumbers}>အမှန်တကယ် တိုင်းတာချက် မရှိသေးလို့ ရာခိုင်နှုန်း မပြပါ။</Text>
          )}
        </View>
      ) : null}

      {error ? (
        <View style={styles.errorCard}>
          <Text accessibilityRole="alert" style={styles.errorTitle}>Download မအောင်မြင်ပါ</Text>
          <Text style={styles.errorStage}>ရပ်သွားတဲ့အဆင့်: {errorStage}</Text>
          <Text selectable style={styles.error}>{error}</Text>
          <Pressable accessibilityRole="button" onPress={runDownload} style={styles.retryButton}>
            <Text style={styles.retryButtonText}>ထပ်စမ်းမည်</Text>
          </Pressable>
        </View>
      ) : null}
      {savedUri ? (
        <View style={styles.successCard}>
          <Text style={styles.successTitle}>Video သိမ်းပြီးပါပြီ</Text>
          <Text selectable style={styles.successPath}>{savedUri}</Text>
        </View>
      ) : null}

      <Text style={styles.footer}>လျှို့ဝှက်ဆက်တင်တွေကို ဒီစက်မှာပဲ သိမ်းပါမယ်။ ရာခိုင်နှုန်းကို တကယ့် byte တိုင်းတာချက်ရှိမှ ပြပါမယ်။</Text>
    </ScrollView>
  );
}

function formatRate(bytesPerSecond: number): string {
  if (bytesPerSecond >= 1024 * 1024) return `${(bytesPerSecond / (1024 * 1024)).toFixed(1)} MB/s`;
  if (bytesPerSecond >= 1024) return `${(bytesPerSecond / 1024).toFixed(1)} KB/s`;
  return `${Math.round(bytesPerSecond)} B/s`;
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: "#0b0f14" },
  content: { flexGrow: 1, paddingHorizontal: 22, paddingTop: 30, paddingBottom: 36 },
  topRow: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", gap: 12 },
  eyebrow: { color: "#51d4c8", fontSize: 10, fontWeight: "800", letterSpacing: 1.8, marginBottom: 7 },
  title: { color: "#f1f6f7", fontSize: 29, fontWeight: "800", letterSpacing: -0.5 },
  settingsButton: { borderWidth: 1, borderColor: "#2a3a40", backgroundColor: "#111a20", borderRadius: 14, paddingHorizontal: 14, paddingVertical: 11 },
  settingsButtonText: { color: "#d7e2e5", fontSize: 13, fontWeight: "700" },
  intro: { color: "#a5b3ba", fontSize: 14, lineHeight: 22, marginTop: 18, marginBottom: 24, maxWidth: 360 },
  serverCard: { flexDirection: "row", alignItems: "center", gap: 12, borderWidth: 1, borderColor: "#27343a", backgroundColor: "#121a20", borderRadius: 18, padding: 16, marginBottom: 20 },
  serverDot: { width: 10, height: 10, borderRadius: 5, backgroundColor: "#687780" },
  serverDotReady: { backgroundColor: "#59d79a" },
  serverCopy: { flex: 1, gap: 4 },
  serverTitle: { color: "#e7eff1", fontSize: 14, fontWeight: "700" },
  serverText: { color: "#94a2a9", fontSize: 12, lineHeight: 18 },
  serverBadge: { color: "#f5bd77", fontSize: 9, fontWeight: "800", letterSpacing: 0.8 },
  serverBadgeReady: { color: "#59d79a" },
  inputCard: { borderWidth: 1, borderColor: "#27343a", backgroundColor: "#121a20", borderRadius: 18, padding: 16, gap: 10 },
  label: { color: "#d6e1e4", fontSize: 13, fontWeight: "700" },
  input: { minHeight: 52, borderRadius: 13, borderWidth: 1, borderColor: "#2d3d43", backgroundColor: "#0b1116", paddingHorizontal: 14, color: "#f1f6f7", fontSize: 14 },
  hint: { color: "#839198", fontSize: 11, lineHeight: 17 },
  downloadButton: { minHeight: 56, borderRadius: 17, backgroundColor: "#51d4c8", alignItems: "center", justifyContent: "center", marginTop: 16 },
  downloadButtonText: { color: "#071012", fontSize: 15, fontWeight: "900" },
  disabled: { opacity: 0.7 },
  notice: { color: "#f5bd77", fontSize: 12, lineHeight: 19, marginTop: 12 },
  progressCard: { borderWidth: 1, borderColor: "#27343a", backgroundColor: "#121a20", borderRadius: 18, padding: 16, marginTop: 20, gap: 12 },
  progressHeader: { flexDirection: "row", alignItems: "center", gap: 10 },
  progressStage: { flex: 1, color: "#dce8ea", fontSize: 13, lineHeight: 19 },
  track: { height: 8, borderRadius: 4, backgroundColor: "#27343a", overflow: "hidden" },
  fill: { height: "100%", backgroundColor: "#51d4c8", borderRadius: 4 },
  progressNumbers: { color: "#94a2a9", fontSize: 11 },
  error: { color: "#ff9677", fontSize: 12, lineHeight: 19, marginTop: 16 },
  errorCard: { borderWidth: 1, borderColor: "#713d37", backgroundColor: "#241518", borderRadius: 18, padding: 16, marginTop: 16, gap: 8 },
  errorTitle: { color: "#ff9677", fontSize: 14, fontWeight: "800" },
  errorStage: { color: "#e4b09f", fontSize: 11, lineHeight: 17 },
  retryButton: { alignSelf: "flex-start", borderWidth: 1, borderColor: "#a75b4e", borderRadius: 11, paddingHorizontal: 13, paddingVertical: 9, marginTop: 3 },
  retryButtonText: { color: "#ffd2c5", fontSize: 12, fontWeight: "800" },
  successCard: { borderWidth: 1, borderColor: "#28533f", backgroundColor: "#102219", borderRadius: 18, padding: 16, marginTop: 18, gap: 8 },
  successTitle: { color: "#71e6a7", fontSize: 14, fontWeight: "800" },
  successPath: { color: "#b2c5ba", fontSize: 11, lineHeight: 17 },
  footer: { color: "#718087", fontSize: 11, lineHeight: 17, marginTop: 28 },
});
