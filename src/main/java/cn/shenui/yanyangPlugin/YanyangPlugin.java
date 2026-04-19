package cn.shenui.yanyangPlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class YanyangPlugin extends JavaPlugin {

    private FileConfiguration config;
    private File configFile;
    private BukkitTask cleanerTask;
    private static final Pattern HEX_PATTERN = Pattern.compile("&([a-f0-9k-or])");
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.builder()
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();

        startCleanerTask();

        System.out.println("\n" +
                "\n" +
                "[YanyangPlugin] 插件正在加载\n" +
                "\n" +
                "--------YanyangPlugin--------\n" +
                "---版本: 1.4-paper-1.20.1----\n" +
                "----------深水6 开发-----------\n" +
                "\n" +
                "[YanyangPlugin] 插件加载成功\n"
        );

    }

    @Override
    public void onDisable() {
        if (cleanerTask != null) {
            cleanerTask.cancel();
        }

        System.out.println("\n" +
                "\n" +
                "[YanyangPlugin] 插件正在卸载\n" +
                "\n" +
                "--------YanyangPlugin--------\n" +
                "---版本: 1.4-paper-1.20.1----\n" +
                "----------深水6 开发-----------\n" +
                "\n" +
                "[YanyangPlugin] 插件已卸载\n"
        );
    }

    private void loadConfig() {
        configFile = new File(getDataFolder(), "config.yml");

        if (!configFile.exists()) {
            saveResource("config.yml", false);
            getLogger().info("已创建默认配置文件");
        }

        config = YamlConfiguration.loadConfiguration(configFile);

        config.addDefault("cleaner.enabled", true);
        config.addDefault("cleaner.threshold", 1000);
        config.addDefault("cleaner.interval", 30);
        config.addDefault("cleaner.message", "&b&l[扫地姬] &6&o&l已清理 %count% 个掉落物！");
        config.addDefault("cleaner.whitelist", List.of(""));
        config.addDefault("cleaner.debug", false);
        config.addDefault("cleaner.count_mode", "entity");

        config.options().copyDefaults(true);
        try {
            config.save(configFile);
        } catch (IOException e) {
            getLogger().severe("无法保存配置文件: " + e.getMessage());
        }

        config = YamlConfiguration.loadConfiguration(configFile);

        boolean enabled = config.getBoolean("cleaner.enabled");
        int threshold = config.getInt("cleaner.threshold");
        int interval = config.getInt("cleaner.interval");
        boolean debug = config.getBoolean("cleaner.debug");
        String countMode = config.getString("cleaner.count_mode");
        List<String> whitelist = config.getStringList("cleaner.whitelist");

        getLogger().info("========== 配置信息 ==========");
        getLogger().info("启用状态: " + enabled);
        getLogger().info("清理阈值: " + threshold);
        getLogger().info("检查间隔: " + interval + "秒");
        getLogger().info("计数模式: " + countMode + " (entity=实体数量, item=物品总数)");
        getLogger().info("调试模式: " + (debug ? "开启" : "关闭"));
        getLogger().info("白名单: " + String.join(", ", whitelist));
        getLogger().info("配置文件路径: " + configFile.getAbsolutePath());
        getLogger().info("==============================");
    }

    private void startCleanerTask() {
        boolean enabled = config.getBoolean("cleaner.enabled");
        int interval = config.getInt("cleaner.interval");
        int threshold = config.getInt("cleaner.threshold");
        boolean debug = config.getBoolean("cleaner.debug");
        String countMode = config.getString("cleaner.count_mode");
        List<String> whitelist = config.getStringList("cleaner.whitelist");

        getLogger().info("准备启动扫地姬任务...");
        getLogger().info("enabled=" + enabled + ", interval=" + interval + ", threshold=" + threshold + ", debug=" + debug + ", countMode=" + countMode);

        if (!enabled) {
            getLogger().warning("扫地姬已禁用，跳过启动");
            return;
        }

        getLogger().info("启动扫地姬任务 - 检查间隔: " + interval + "秒, 清理阈值: " + threshold);
        getLogger().info("白名单物品: " + String.join(", ", whitelist));

        cleanerTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            int totalEntities = 0;
            int totalItems = 0;
            int worldCount = 0;

            for (World world : Bukkit.getWorlds()) {
                Collection<Item> entities = world.getEntitiesByClass(Item.class);

                List<Item> validItems = entities.stream()
                        .filter(item -> item != null && !item.isDead() && item.isValid())
                        .collect(Collectors.toList());

                int entityCount = validItems.size();
                int itemCount = validItems.stream()
                        .mapToInt(item -> item.getItemStack().getAmount())
                        .sum();

                totalEntities += entityCount;
                totalItems += itemCount;
                worldCount++;

                if (debug && entityCount > 0) {
                    StringBuilder itemDetails = new StringBuilder();
                    for (Item item : validItems) {
                        if (itemDetails.length() > 0) itemDetails.append(", ");
                        itemDetails.append(item.getItemStack().getType().name())
                                .append(" x").append(item.getItemStack().getAmount());
                    }
                    getLogger().info("[调试] 世界 [" + world.getName() + "] 有 " + entityCount + " 个掉落物实体, " + itemCount + " 个物品: " + itemDetails.toString());
                }
            }

            int displayCount = countMode.equalsIgnoreCase("item") ? totalItems : totalEntities;

            if (debug) {
                getLogger().info("========== 检测结果 ==========");
                getLogger().info("[调试] 掉落物实体数: " + totalEntities);
                getLogger().info("[调试] 物品总数: " + totalItems);
                getLogger().info("[调试] 计数模式: " + countMode + ", 用于判断的数量: " + displayCount);
                getLogger().info("[调试] 清理阈值: " + threshold);
                getLogger().info("[调试] 是否触发: " + (displayCount >= threshold ? "是" : "否"));
                getLogger().info("==============================");
            }

            if (displayCount >= threshold) {
                int cleanedEntities = 0;
                int cleanedItems = 0;
                int whitelistedCount = 0;

                if (debug) {
                    getLogger().info("[调试] 达到阈值，开始清理...");
                }

                for (World world : Bukkit.getWorlds()) {
                    Collection<Item> entities = world.getEntitiesByClass(Item.class);

                    for (Item item : entities) {
                        if (item == null || item.isDead() || !item.isValid()) {
                            continue;
                        }

                        String itemName = item.getItemStack().getType().name().toLowerCase();
                        boolean isWhitelisted = whitelist.stream()
                                .anyMatch(whitelistItem -> itemName.contains(whitelistItem.toLowerCase()));

                        if (isWhitelisted) {
                            whitelistedCount++;
                        } else {
                            int amount = item.getItemStack().getAmount();
                            item.remove();
                            cleanedEntities++;
                            cleanedItems += amount;
                        }
                    }
                }

                if (debug) {
                    getLogger().info("[调试] 清理完成 - 清理实体: " + cleanedEntities + ", 清理物品: " + cleanedItems + ", 白名单保留: " + whitelistedCount);
                }

                if (cleanedEntities > 0) {
                    String message = config.getString("cleaner.message")
                            .replace("%count%", String.valueOf(countMode.equalsIgnoreCase("item") ? cleanedItems : cleanedEntities));

                    Component coloredMessage = parseColor(message);

                    Bukkit.broadcast(coloredMessage);

                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.sendMessage(coloredMessage);
                    }

                    getLogger().info("✓ 已清理 " + cleanedEntities + " 个掉落物实体 (共 " + cleanedItems + " 个物品)，消息已发送到聊天栏");
                } else {
                    getLogger().info("所有掉落物都在白名单中，未清理任何物品");
                }
            } else if (debug) {
                getLogger().info("[调试] 未达到阈值，不清理");
            }
        }, 20L * interval, 20L * interval);

        getLogger().info("扫地姬任务已成功启动！");
    }

    private Component parseColor(String message) {
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            String colorCode = matcher.group(1);
            char replacement = colorCode.charAt(0);
            matcher.appendReplacement(buffer, "§" + replacement);
        }
        matcher.appendTail(buffer);

        return LEGACY_SERIALIZER.deserialize(buffer.toString());
    }

    public void reloadConfig() {
        loadConfig();
        if (cleanerTask != null) {
            cleanerTask.cancel();
        }
        startCleanerTask();
    }
}
