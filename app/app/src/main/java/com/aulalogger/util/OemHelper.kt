package com.aulalogger.util

import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * CONF-007: detecta OEMs conhecidos por matar foreground services agressivamente
 * e expõe deeplinks para a tela de exceção de bateria/auto-start específica.
 *
 * Mesmo com FGS + WakeLock + battery optimization exemption, fabricantes como
 * Xiaomi (MIUI), Huawei (EMUI), Vivo (FunTouch), Oppo (ColorOS) e Samsung (OneUI)
 * têm whitelists separadas que precisam ser configuradas manualmente pelo usuário.
 */
object OemHelper {

    enum class Oem(val displayName: String, val manualPath: String) {
        XIAOMI(
            "Xiaomi / Redmi (MIUI)",
            "Configurações → Apps → Gerenciar apps → AulaLogger → " +
                "Auto-start (ativar) e Economia de bateria → Sem restrições"
        ),
        HUAWEI(
            "Huawei / Honor (EMUI)",
            "Configurações → Bateria → Iniciar apps → AulaLogger → " +
                "Gerenciar manualmente: Auto-início + Inicialização secundária + Atividade em background"
        ),
        VIVO(
            "Vivo / iQOO (FunTouch)",
            "Configurações → Bateria → Alto consumo em background → AulaLogger → Permitir"
        ),
        OPPO(
            "Oppo / Realme (ColorOS)",
            "Configurações → Bateria → Otimização → AulaLogger → Não otimizar"
        ),
        SAMSUNG(
            "Samsung (OneUI)",
            "Configurações → Apps → AulaLogger → Bateria → Sem restrição. " +
                "Adicione também a 'Apps que não dormem' nas configurações de bateria."
        ),
        ASUS("Asus (ZenUI)", "Configurações → Apps → AulaLogger → Permitir auto-start"),
        OTHER("Outro", "Confira em Configurações se há restrições de bateria ou auto-start para este app.")
    }

    fun detect(): Oem {
        val mfr = Build.MANUFACTURER.lowercase()
        return when {
            mfr.contains("xiaomi") || mfr.contains("redmi") || mfr.contains("poco") -> Oem.XIAOMI
            mfr.contains("huawei") || mfr.contains("honor") -> Oem.HUAWEI
            mfr.contains("vivo") || mfr.contains("iqoo") -> Oem.VIVO
            mfr.contains("oppo") || mfr.contains("realme") -> Oem.OPPO
            mfr.contains("samsung") -> Oem.SAMSUNG
            mfr.contains("asus") -> Oem.ASUS
            else -> Oem.OTHER
        }
    }

    /** Indica se o OEM atual é conhecido por ser agressivo com FGS. */
    fun isAggressive(): Boolean = detect() != Oem.OTHER

    /**
     * Tenta abrir a tela de "auto-start" do OEM. Se falhar (deeplink não
     * existe), retorna false e o caller mostra instruções manuais.
     */
    fun openAutoStartSettings(context: Context): Boolean {
        val intents: List<Intent> = when (detect()) {
            Oem.XIAOMI -> listOf(
                Intent().setClassName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            )
            Oem.HUAWEI -> listOf(
                Intent().setClassName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                )
            )
            Oem.VIVO -> listOf(
                Intent().setClassName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                )
            )
            Oem.OPPO -> listOf(
                Intent().setClassName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                )
            )
            Oem.SAMSUNG -> listOf(
                Intent().setClassName(
                    "com.samsung.android.lool",
                    "com.samsung.android.sm.ui.battery.BatteryActivity"
                )
            )
            else -> emptyList()
        }
        for (intent in intents) {
            try {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                return true
            } catch (_: Throwable) {
                // tenta próximo
            }
        }
        return false
    }
}
