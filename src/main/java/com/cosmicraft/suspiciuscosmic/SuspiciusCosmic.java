package com.cosmicraft.suspiciuscosmic;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class SuspiciusCosmic extends JavaPlugin implements Listener, CommandExecutor {

    private final Map<UUID, PlayerData> playerDataMap = new HashMap<>();
    private DiscordWebhook webhook;

    // Config values
    private int camperRadius;
    private int camperAlertIntervalMinutes;
    private int combatLogSeconds;
    private List<String> evasionCommands;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigValues();

        getServer().getPluginManager().registerEvents(this, this);
        getCommand("sospechoso").setExecutor(this);
        getCommand("suspiciuscosmic").setExecutor(this);

        // Task to check campers and clean old events (runs every 10 seconds = 200 ticks)
        getServer().getScheduler().runTaskTimer(this, this::checkPlayersTask, 200L, 200L);

        getLogger().info("SuspiciusCosmic activado correctamente.");
    }

    private void loadConfigValues() {
        reloadConfig();
        webhook = new DiscordWebhook(getConfig().getString("discord-webhook-url", ""));
        camperRadius = getConfig().getInt("camper.radius", 500);
        camperAlertIntervalMinutes = getConfig().getInt("camper.alert-interval-minutes", 30);
        combatLogSeconds = getConfig().getInt("combat-log.seconds", 30);
        evasionCommands = getConfig().getStringList("combat-log.evasion-commands");
    }

    @Override
    public void onDisable() {
        getLogger().info("SuspiciusCosmic desactivado.");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        playerDataMap.put(p.getUniqueId(), new PlayerData(p.getLocation(), System.currentTimeMillis()));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        PlayerData data = playerDataMap.remove(p.getUniqueId());

        if (data != null) {
            data.addTimelineEvent("Se desconectó del servidor.");
            checkAndSendEvasion(p, data, "Actitud Sospechosa (Evadir Muerte - Desconexión)");
        }
    }

    @EventHandler
    public void onPlayerChangeWorld(PlayerChangedWorldEvent event) {
        Player p = event.getPlayer();
        PlayerData data = playerDataMap.get(p.getUniqueId());
        if (data != null) {
            data.setReferenceLocation(p.getLocation());
            data.setReferenceStartTime(System.currentTimeMillis());
            data.clearAlertedMinutes();
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player p = (Player) event.getEntity();
            PlayerData data = playerDataMap.get(p.getUniqueId());
            if (data != null) {
                // Add event to timeline
                data.addTimelineEvent("Recibió " + String.format("%.1f", event.getFinalDamage()) + " de daño.");
            }
        }
    }

    @EventHandler
    public void onEntityResurrect(EntityResurrectEvent event) {
        if (event.getEntity() instanceof Player && !event.isCancelled()) {
            Player p = (Player) event.getEntity();
            PlayerData data = playerDataMap.get(p.getUniqueId());
            if (data != null) {
                data.addTimelineEvent("💥 ¡Usó un Tótem de Inmortalidad!");
            }
        }
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player p = event.getPlayer();
        String message = event.getMessage().toLowerCase();
        String command = message.split(" ")[0];

        for (String evCmd : evasionCommands) {
            if (command.equalsIgnoreCase(evCmd)) {
                PlayerData data = playerDataMap.get(p.getUniqueId());
                if (data != null) {
                    data.addTimelineEvent("Ejecutó comando evasivo: `" + event.getMessage() + "`");
                    checkAndSendEvasion(p, data, "Actitud Sospechosa (Evadir Muerte con Comando)");
                }
                break;
            }
        }
    }

    private void checkAndSendEvasion(Player p, PlayerData data, String title) {
        List<PlayerData.TimelineEvent> recentEvents = data.getEventsSince(combatLogSeconds * 1000L);
        // Si hay más de un evento (el daño + la desconexion/comando) o si hay daño reciente
        boolean hasDamageOrTotem = recentEvents.stream().anyMatch(e -> e.description.contains("daño") || e.description.contains("Tótem"));
        
        if (hasDamageOrTotem) {
            StringBuilder timelineStr = new StringBuilder();
            timelineStr.append("**Jugador:** ").append(p.getName()).append("\n\n**Línea de Tiempo (últimos ").append(combatLogSeconds).append("s):**\n");
            
            long now = System.currentTimeMillis();
            for (PlayerData.TimelineEvent ev : recentEvents) {
                long diffSeconds = (now - ev.timestamp) / 1000;
                timelineStr.append("- Hace ").append(diffSeconds).append("s: ").append(ev.description).append("\n");
            }

            sendWebhookAsync(title, timelineStr.toString(), 16711680); // Red color
        }
    }

    private void checkPlayersTask() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData data = playerDataMap.get(p.getUniqueId());
            if (data == null) continue;

            // Keep only last 5 minutes of events
            data.cleanOldEvents(300_000L);

            Location currentLoc = p.getLocation();
            Location refLoc = data.getReferenceLocation();

            if (!currentLoc.getWorld().equals(refLoc.getWorld())) {
                data.setReferenceLocation(currentLoc);
                data.setReferenceStartTime(now);
                data.clearAlertedMinutes();
                continue;
            }

            double dist2D = getDistance2D(currentLoc, refLoc);
            if (dist2D > camperRadius) {
                data.setReferenceLocation(currentLoc);
                data.setReferenceStartTime(now);
                data.clearAlertedMinutes();
            } else {
                long diffMillis = now - data.getReferenceStartTime();
                long minutesInside = diffMillis / (1000 * 60);

                if (camperAlertIntervalMinutes > 0 && minutesInside >= camperAlertIntervalMinutes) {
                    long currentInterval = (minutesInside / camperAlertIntervalMinutes) * camperAlertIntervalMinutes;
                    if (!data.getAlertedMinutes().contains(currentInterval)) {
                        data.getAlertedMinutes().add(currentInterval);
                        
                        long h = currentInterval / 60;
                        long m = currentInterval % 60;
                        String timeStr = (h > 0 ? h + "h " : "") + (m > 0 || h == 0 ? m + "m" : "");
                        
                        sendWebhookAsync(
                                "Actitud Sospechosa (Campeando / AFK)",
                                "**Jugador:** " + p.getName() + "\n" +
                                "- No ha salido de un rango de " + camperRadius + " bloques en **" + timeStr + "**.\n" +
                                "- Distancia máxima de su centro: " + String.format("%.1f", dist2D) + " bloques.",
                                16776960 // Yellow color
                        );
                    }
                }
            }
        }
    }

    private double getDistance2D(Location loc1, Location loc2) {
        return Math.sqrt(Math.pow(loc1.getX() - loc2.getX(), 2) + Math.pow(loc1.getZ() - loc2.getZ(), 2));
    }

    private void sendWebhookAsync(String title, String description, int color) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            if (webhook != null) {
                webhook.sendEmbed(title, description, color);
            }
        });
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("suspiciuscosmic")) {
            loadConfigValues();
            sender.sendMessage(ChatColor.GREEN + "Configuración de SuspiciusCosmic recargada.");
            return true;
        }

        if (command.getName().equalsIgnoreCase("sospechoso")) {
            if (args.length == 0) {
                sender.sendMessage(ChatColor.RED + "Uso: /sospechoso <jugador|test>");
                return true;
            }

            if (args[0].equalsIgnoreCase("test")) {
                sender.sendMessage(ChatColor.YELLOW + "Enviando embed de prueba a Discord...");
                sendWebhookAsync(
                    "Test de Webhook", 
                    "**Jugador:** " + sender.getName() + "\n\n**Línea de Tiempo (últimos 30s):**\n- Hace 25s: Recibió 15.0 de daño.\n- Hace 10s: 💥 ¡Usó un Tótem de Inmortalidad!\n- Hace 0s: Ejecutó comando evasivo: `/tpa admin`", 
                    65280
                );
                return true;
            }

            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Jugador no encontrado u offline.");
                return true;
            }

            PlayerData data = playerDataMap.get(target.getUniqueId());
            if (data == null) {
                sender.sendMessage(ChatColor.RED + "No hay datos para ese jugador.");
                return true;
            }

            long diffMillis = System.currentTimeMillis() - data.getReferenceStartTime();
            long hours = diffMillis / (1000 * 60 * 60);
            long minutes = (diffMillis / (1000 * 60)) % 60;
            
            double dist = getDistance2D(target.getLocation(), data.getReferenceLocation());

            sender.sendMessage(ChatColor.YELLOW + "=== Inactividad de " + target.getName() + " ===");
            sender.sendMessage(ChatColor.GRAY + "Tiempo en la zona: " + ChatColor.WHITE + hours + " horas y " + minutes + " minutos.");
            sender.sendMessage(ChatColor.GRAY + "Distancia de su punto de referencia: " + ChatColor.WHITE + String.format("%.1f", dist) + " bloques.");
            sender.sendMessage(ChatColor.GRAY + "Límite: " + ChatColor.WHITE + camperRadius + " bloques.");
            return true;
        }

        return false;
    }
}
