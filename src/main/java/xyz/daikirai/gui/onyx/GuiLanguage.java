package xyz.daikirai.gui.onyx;

import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tracks the vanilla language so the ClickGUI can switch between English and Chinese. */
public final class GuiLanguage {

    private static final Map<String, String> ZH = new HashMap<String, String>();
    private static final Pattern COUNT = Pattern.compile("^(\\d+) (world|worlds|server|servers)$");

    static {
        put("Singleplayer", "单人游戏");
        put("Multiplayer", "多人游戏");
        put("Alt Manager", "账户管理");
        put("Settings", "设置");
        put("Options", "选项");
        put("Mods", "模组");
        put("Proxy", "代理");
        put("Background", "背景");
        put("Quit Game", "退出游戏");
        put("Video, controls and language", "视频、控制和语言");
        put("Forge mod list", "Forge 模组列表");
        put("SOCKS5 and HTTP tunnel", "SOCKS5 和 HTTP 隧道");
        put("Pick the menu wallpaper", "选择菜单壁纸");
        put("Close the client", "关闭客户端");
        put("Accent", "强调色");
        put("Background tint", "背景色调");
        put("Scale", "缩放");
        put("Lyrics", "歌词");
        put("Music Player", "网易云音乐");
        put("Distance", "距离");
        put("Side offset", "横向偏移");
        put("Height", "高度");
        put("Show translation", "显示翻译");
        put("Follow GUI scale", "跟随界面缩放");
        put("Frosted glass", "毛玻璃");
        put("Liquid glass", "液态玻璃");
        put("Surface theme", "界面主题");
        put("Frost opacity", "毛玻璃不透明度");
        put("Liquid opacity", "液态玻璃不透明度");
        put("Liquid frost amount", "液态玻璃磨砂程度");
        put("Coloured frost", "彩色毛玻璃");
        put("Matte liquid glass", "磨砂液态玻璃");
        put("Dark", "深色");
        put("Light", "浅色");
        put("Dark Blue", "深蓝");
        put("Midnight", "午夜");
        put("Dark Purple", "深紫");
        put("Sea", "海洋");
        put("Sakura", "樱花");
        put("Auto swap", "自动切换方块");
        put("Disable SafeWalk", "关闭边缘保护");
        put("Show activation hitbox", "显示激活区域");
        put("Print", "显示状态提示");
        put("Reset window", "重置窗口");
        put("Bind", "绑定");
        put("Toggle", "切换");
        put("Hold", "按住");
        put("Press a key...", "按下按键...");
        put("None", "无");
        put("This module has no settings", "此模块没有设置");
        put("No modules match", "没有匹配的模块");
        put("No modules here yet", "这里还没有模块");
        put("Search modules", "搜索模块");
        put("Theme", "主题");
        put("Follow theme", "跟随主题");
        put("Custom", "自定义");
        put("Cancel", "取消");
        put("Save", "保存");
        put("Delete", "删除");
        put("Add", "添加");
        put("Edit", "编辑");
        put("Refresh", "刷新");
        put("Join", "加入");
        put("Play", "进入游戏");
        put("Create New World", "创建新世界");
        put("Add server", "添加服务器");
        put("Direct connect", "直接连接");
        put("Edit server", "编辑服务器");
        put("Server name", "服务器名称");
        put("Server address", "服务器地址");
        put("Name and address of the server", "服务器名称和地址");
        put("Accounts", "账户");
        put("Search accounts", "搜索账户");
        put("No accounts yet", "还没有账户");
        put("No matching accounts", "没有匹配的账户");
        put("Offline", "离线");
        put("Access token", "访问令牌");
        put("Microsoft", "微软账户");
        put("Add account", "添加账户");
        put("Username", "用户名");
        put("Minecraft access token", "Minecraft 访问令牌");
        put("Microsoft OAuth refresh token", "Microsoft OAuth 刷新令牌");
        put("Sign in with Microsoft", "使用 Microsoft 登录");
        put("Copy link", "复制链接");
        put("Proxy settings", "代理设置");
        put("Restore launcher", "恢复启动器");
        put("Done", "完成");
        put("Language", "语言");
        put("Fullscreen", "全屏");
        put("On", "开启");
        put("Off", "关闭");
        put("Key bindings", "按键绑定");
        put("Resource packs", "资源包");
        put("Skin customization", "皮肤自定义");
        put("Textures and sounds", "纹理和声音");
        put("Cape, jacket, sleeves", "披风、外套、袖子");
        put("Movement, inventory, chat", "移动、物品栏、聊天");
        put("Finished", "完成");
        put("Taking items", "正在取物品");
        put("Keybinds", "按键绑定");
        put("Now playing", "正在播放");
        put("Onyx clouds", "Onyx 云雾");
        put("Theme coloured cloud shader", "随主题色变化的云雾着色效果");
        put("Android gradient", "Android 渐变");
        put("Flat wallpaper in the accent colour", "使用强调色的纯色渐变背景");
        put("Cyrene", "Cyrene");
        put("Rainy Day", "雨日");
        put("Wallpaper Engine scene, 1920x1080 still", "Wallpaper Engine 场景静态图，1920×1080");
        put("Dock colour", "快捷栏颜色");
        put("Follows the wallpaper", "跟随壁纸");
        put("Custom colour", "自定义颜色");
        put("Dark mode", "深色模式");
        put("Black theme for the menus", "菜单使用黑色主题");
        put("Background blur", "背景模糊");
        put("Only affects wallpapers", "仅对壁纸生效");
        put("Mica", "云母效果");
        put("Clock face", "时钟样式");
        put("Analog", "指针");
        put("Digital", "数字");
        put("Menu background", "菜单背景");
        put("Analog clock", "指针时钟");
        put("Digital clock", "数字时钟");
        put("Welcome to your", "欢迎使用");
        put("Accessibility", "辅助功能");
        put("Get started", "开始使用");
        put("English (United States)", "英语（美国）");
        put("No worlds yet - create one to start", "还没有世界，创建一个开始游戏");
        put("No servers yet - add one to join", "还没有服务器，添加一个加入游戏");
        put("Create New World", "创建新世界");
        put("Re-Create", "重新创建");
        put("Delete world?", "删除世界？");
        put("Remove server?", "移除服务器？");
        put("Remove", "移除");
        put("Close", "关闭");
        put("Back", "返回");
        put("Menu style", "菜单样式");
        put("Refresh tokens", "刷新令牌");
        put("Log in", "登录");
        put("Copy username", "复制用户名");
        put("Operation failed", "操作失败");
        put("Could not read the world list", "无法读取世界列表");
        put("Use proxy", "使用代理");
        put("Use FisProxy for game connections", "使用 FisProxy 连接游戏");
        put("No session", "没有会话");
        put("Start session", "启动会话");
        put("Stop session", "停止会话");
        put("Change IP", "更换 IP");
        put("Apply", "应用");
        put("Test", "测试");
        put("FisProxy disabled", "FisProxy 已禁用");
        put("Proxy settings applied", "代理设置已应用");
        put("Proxy disabled", "代理已禁用");
        put("Testing proxy...", "正在测试代理...");
        put("Proxy connection verified", "代理连接已验证");
        put("Server address, host or host:port", "服务器地址、主机或主机:端口");
    }

    private static void put(String en, String zh) {
        ZH.put(en, zh);
    }

    private GuiLanguage() {
    }

    static boolean simplifiedChinese() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.gameSettings == null || mc.gameSettings.language == null) {
            return false;
        }
        return mc.gameSettings.language.toLowerCase(Locale.ROOT).startsWith("zh_cn");
    }

    /** Translates custom launcher/UI labels while preserving game terminology and user text. */
    public static String translate(String text) {
        if (!simplifiedChinese() || text == null || text.isEmpty()) {
            return text;
        }
        String translated = ZH.get(text);
        if (translated != null) {
            return translated;
        }
        Matcher count = COUNT.matcher(text);
        if (count.matches()) {
            String number = count.group(1);
            return number + (count.group(2).startsWith("world") ? " 个世界" : " 个服务器");
        }
        return text;
    }
}
