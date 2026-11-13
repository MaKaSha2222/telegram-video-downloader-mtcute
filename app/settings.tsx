import { useEffect, useState } from "react";
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from "react-native";
import { useRouter } from "expo-router";

import { loadTelegramCredentials, saveTelegramCredentials } from "@/lib/secure-config";

export default function SettingsScreen() {
  const router = useRouter();
  const [apiId, setApiId] = useState("");
  const [apiHash, setApiHash] = useState("");
  const [botToken, setBotToken] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState("");
  const [messageIsError, setMessageIsError] = useState(false);

  useEffect(() => {
    let active = true;
    loadTelegramCredentials()
      .then((saved) => {
        if (!active || !saved) return;
        setApiId(saved.apiId);
        setApiHash(saved.apiHash);
        setBotToken(saved.botToken);
      })
      .catch(() => {
        if (active) {
          setMessage("သိမ်းထားတဲ့ ဆက်တင်တွေကို ဖတ်မရပါ။");
          setMessageIsError(true);
        }
      })
      .finally(() => active && setLoading(false));
    return () => {
      active = false;
    };
  }, []);

  const onSave = async () => {
    setSaving(true);
    setMessage("");
    try {
      await saveTelegramCredentials({ apiId, apiHash, botToken });
      setMessage("ဆက်တင်တွေကို ဒီ Android စက်ရဲ့ လုံခြုံတဲ့နေရာမှာ သိမ်းပြီးပါပြီ။");
      setMessageIsError(false);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "ဆက်တင်တွေကို သိမ်းမရပါ။");
      setMessageIsError(true);
    } finally {
      setSaving(false);
    }
  };

  return (
    <ScrollView style={styles.root} contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
      <View style={styles.header}>
        <Pressable accessibilityRole="button" onPress={() => router.back()} style={styles.backButton}>
          <Text style={styles.backText}>← နောက်သို့</Text>
        </Pressable>
        <Text style={styles.eyebrow}>APP ဆက်တင်များ</Text>
        <Text style={styles.title}>Telegram API</Text>
        <Text style={styles.subtitle}>သင့်အချက်အလက်တွေကို ဒီ Android စက်မှာ လုံခြုံစွာ သိမ်းထားမယ်။</Text>
      </View>

      {loading ? (
        <ActivityIndicator color="#51d4c8" style={styles.loader} />
      ) : (
        <View style={styles.form}>
          <CredentialField label="API ID" value={apiId} onChangeText={setApiId} keyboardType="number-pad" />
          <CredentialField label="API Hash" value={apiHash} onChangeText={setApiHash} secureTextEntry />
          <CredentialField label="Bot Token" value={botToken} onChangeText={setBotToken} secureTextEntry />

          {message ? <Text style={[styles.message, messageIsError && styles.error]}>{message}</Text> : null}

          <Pressable accessibilityRole="button" onPress={onSave} disabled={saving} style={[styles.saveButton, saving && styles.disabled]}>
            {saving ? <ActivityIndicator color="#081011" /> : <Text style={styles.saveText}>လုံခြုံစွာ သိမ်းမည်</Text>}
          </Pressable>
          <Text style={styles.privacy}>ဒီတန်ဖိုးတွေကို backend ဆီ မပို့ပါ။ log နဲ့ screen ပေါ်မှာ လျှို့ဝှက်တန်ဖိုးတွေ မပြပါ။</Text>
        </View>
      )}
    </ScrollView>
  );
}

type CredentialFieldProps = {
  label: string;
  value: string;
  onChangeText: (value: string) => void;
  secureTextEntry?: boolean;
  keyboardType?: "default" | "number-pad";
};

function CredentialField({ label, value, onChangeText, secureTextEntry, keyboardType = "default" }: CredentialFieldProps) {
  return (
    <View style={styles.field}>
      <Text style={styles.label}>{label}</Text>
      <TextInput
        accessibilityLabel={label}
        value={value}
        onChangeText={onChangeText}
        secureTextEntry={secureTextEntry}
        keyboardType={keyboardType}
        autoCapitalize="none"
        autoCorrect={false}
        placeholder={`${label} ထည့်ပါ`}
        placeholderTextColor="#687780"
        style={styles.input}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: "#0b0f14" },
  content: { flexGrow: 1, paddingHorizontal: 24, paddingTop: 32, paddingBottom: 40 },
  header: { marginBottom: 32 },
  backButton: { alignSelf: "flex-start", paddingVertical: 8, paddingRight: 14, marginBottom: 20 },
  backText: { color: "#a5b3ba", fontSize: 15, fontWeight: "600" },
  eyebrow: { color: "#51d4c8", fontSize: 11, fontWeight: "800", letterSpacing: 1.6, marginBottom: 8 },
  title: { color: "#f1f6f7", fontSize: 32, fontWeight: "800", letterSpacing: -0.5 },
  subtitle: { color: "#a5b3ba", fontSize: 14, lineHeight: 21, marginTop: 10, maxWidth: 340 },
  loader: { marginTop: 34 },
  form: { gap: 18 },
  field: { gap: 8 },
  label: { color: "#d6e1e4", fontSize: 13, fontWeight: "700" },
  input: { minHeight: 52, borderRadius: 14, borderWidth: 1, borderColor: "#27343a", backgroundColor: "#121a20", paddingHorizontal: 15, color: "#f1f6f7", fontSize: 15 },
  saveButton: { minHeight: 54, borderRadius: 16, backgroundColor: "#51d4c8", alignItems: "center", justifyContent: "center", marginTop: 8 },
  saveText: { color: "#081011", fontSize: 15, fontWeight: "800" },
  disabled: { opacity: 0.65 },
  message: { color: "#93e5db", fontSize: 13, lineHeight: 20 },
  error: { color: "#ff9e7a" },
  privacy: { color: "#839198", fontSize: 12, lineHeight: 18, marginTop: 2 },
});
