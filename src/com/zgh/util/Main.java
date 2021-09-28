package com.zgh.util;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    private static Map<String, String> COMMAND_MAP = new HashMap<>();
    private static JTextArea area;

    static {
        COMMAND_MAP.put("huawei", "adb shell dumpsys activity | grep -i \"mLastResumedActivity\"");
        COMMAND_MAP.put("xiaomi", "adb shell dumpsys activity | grep -i \"topActivity\"");
        COMMAND_MAP.put("vivo", "adb shell  dumpsys activity | grep -i \"mResumedActivity\"");
        COMMAND_MAP.put("oppo", "adb shell  dumpsys activity | grep -i mCurrentFocus=Window");
    }

    private static final String DEFAULT_COMMAND = "adb shell dumpsys activity | grep -i run";

    private static String currentActivityName;
    private static JLabel stateLabel;
    //   private static JLabel showLabel;
    private volatile static boolean getError = false;

    public static void main(String[] args) {
        JFrame jFrame = new JFrame();
        jFrame.setSize(600, 400);
        jFrame.setTitle("获取运行Activity工具");

        //屏幕中央显示
        jFrame.setLocation(ShowUtil.showScreenCenter(jFrame));
        area = new JTextArea();
        area.setLineWrap(true);
        area.setSize(500, 500);
        area.setFont(new java.awt.Font("Dialog", 1, 16));
        doGetActivityName(null);

        stateLabel = new JLabel();
        stateLabel.setFont(new java.awt.Font("Dialog", 1, 18));

        BorderLayout bl = new BorderLayout(40, 40);
        jFrame.getContentPane().setLayout(bl);


        jFrame.getContentPane().add(BorderLayout.CENTER, area);
        jFrame.getContentPane().add(BorderLayout.NORTH, stateLabel);


        JButton jbt = new JButton();
        jbt.setFocusPainted(false);
        jbt.setSize(100, 50);
        jbt.setLocation(100, 100);
        jbt.setText("复制Activity  ( Alt + 1 )");
        jbt.setMnemonic(KeyEvent.VK_1);
        jbt.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                CopyUtil.setClipboardString(currentActivityName);
                showState("复制成功", null, 3000);
            }
        });

        JButton jbt2 = new JButton();
        jbt2.setFocusPainted(false);
        jbt2.setSize(100, 50);
        jbt2.setLocation(100, 100);
        jbt2.setText("重新获取并复制Activity  ( Alt + 2 )");
        jbt2.setMnemonic(KeyEvent.VK_2);
        jbt2.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                doGetActivityName(() -> {
                    CopyUtil.setClipboardString(currentActivityName);
                    showState("复制成功", null, 3000);
                });
            }
        });
        JPanel jPanel = new JPanel();
        jPanel.setLayout(new FlowLayout());
        jPanel.add(jbt);
        jPanel.add(jbt2);

        jFrame.getContentPane().add(BorderLayout.SOUTH, jPanel);

        jFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        jFrame.show();
    }

    private static void doGetActivityName(Action action) {
        getCurrentActivityName(info -> {
            if (getError) {
                currentActivityName = null;
                return;
            }
            currentActivityName = info;
            area.setText("当前Activity为:\n\n");
            area.append(info);
            getFragmentName(info, names -> {

                if (names == null || names.size() == 0) {
                    area.append("\n\n该Activity不包含fragment");
                } else {
                    area.append("\n\n该Activity包含以下fragment:\n\n");
                    for (String name : names) {
                        area.append(name + "\n");
                    }
                }
                if (action != null) {
                    action.call();
                }
            });
        });
    }

    private static void showState(String state, Color color, long hideTime) {
        stateLabel.setText(state);
        if (color != null) {
            stateLabel.setForeground(color);
        }
        if (hideTime > 0) {
            new Thread(() -> {
                try {
                    Thread.sleep(hideTime);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                String text = stateLabel.getText();
                if (text != null && text.equals(state)) {
                    stateLabel.setText("");
                }
            }).start();
        }
    }


    interface NameGetListener {
        void onNameGet(String info);
    }

    interface FragmentNameGetListener {
        void onNameGet(java.util.List<String> names);
    }

    interface Action {
        void call();
    }

    private static void getCurrentActivityName(NameGetListener nameGetListener) {
        getError = false;
        //获取手机品牌
        execute("adb shell getprop | grep ro.product.manufacturer", new CommandResultImpl() {
            @Override
            public void success(String info) {
                String commandForPhone = getCommandForPhone(info);
                execute(commandForPhone, new CommandResultImpl() {
                    @Override
                    public void success(String info) {
                        nameGetListener.onNameGet(getActivityName(info));
                    }
                });
            }
        });
    }

    private static void getFragmentName(String activityName, FragmentNameGetListener nameGetListener) {
        getError = false;
        //获取手机品牌
        execute("adb shell  dumpsys activity " + activityName, new CommandResultImpl() {
            @Override
            public void success(String info) {
                List<String> names = new ArrayList<>();
                //使用正则表达式分析数据
                String pattern = "#\\d:\\s*([\\w]*\\{\\w*\\})";
                Pattern r = Pattern.compile(pattern);
                Matcher m = r.matcher(info);
                String str = null;
                int matcher_start = 0;
                //里面会有多个Added Fragment，只需要最后出现的一个
                while (m.find(matcher_start)) {
                    names.add(m.group(1));
                    matcher_start = m.end();
                }


                System.out.println(str);
                if (nameGetListener != null) {
                    nameGetListener.onNameGet(names);
                }


            }
        });
    }


    private static String getCommandForPhone(String info) {
        //根据品牌获取命令
        String phone = info.toLowerCase();
        String command = null;
        for (String manufacturer : COMMAND_MAP.keySet()) {
            if (phone.contains(manufacturer)) {
                command = COMMAND_MAP.get(manufacturer);
                break;
            }
        }
        if (command == null) {
            command = DEFAULT_COMMAND;
        }
        return command;
    }

    private static String getActivityName(String info) {
        // 按指定模式在字符串查找

        String pattern = "/([\\w.]+)";

        // 创建 Pattern 对象
        Pattern r = Pattern.compile(pattern);

        // 现在创建 matcher 对象
        Matcher m = r.matcher(info);
        if (m.find()) {
            String name = m.group(1);
            /**
             * 这种方式获取到的只有.activity.NewsDetailActivit 没有包名，需要加上包名
             * mLastResumedActivity=ActivityRecord{9785b44 u0 com.cmstop.jhrb/.activity.NewsDetailActivity t94446}
             */
            if (name.startsWith(".")) {
                String pattern2 = "\\s([\\w\\.]*)\\/";
                Pattern r2 = Pattern.compile(pattern2);
                Matcher m2 = r2.matcher(info);
                if (m2.find()) {
                    String packName = m2.group(1);
                    return packName + name;
                }

            }
            return name;

        } else {
            return "无法获取";
        }
    }

    private static class CommandResultImpl implements CommandResult {


        @Override
        public void success(String info) {

        }

        @Override
        public void onError(String error) {
            if (error == null || error.equals("")) {
                return;
            }
            getError = true;
            area.setText("");
            showState(getSimpleInfo(error), Color.RED, 0);
        }

        String getSimpleInfo(String error) {
            if (error == null) {
                return "null";
            }
            if (error.contains("more than one device")) {
                return "不支持多台设备";
            }
            if (error.contains("no devices")) {
                return "请连接设备";
            }
            return error;
        }
    }


    interface CommandResult {
        void success(String info);

        void onError(String error);
    }

    private static void execute(String conmand, CommandResult result) {
        try {
            Process process = Runtime.getRuntime().exec(conmand);
            showInfo(process.getErrorStream(), result::onError);
            showInfo(process.getInputStream(), result::success);
        } catch (Exception e) {
            result.onError(e.getMessage());
        }


    }

    static void showInfo(InputStream inputStream, InfoAction action) {
        new Thread(() -> {
            BufferedReader bufferedReader = null;
            StringBuffer sb = new StringBuffer();
            try {
                bufferedReader = new BufferedReader(new InputStreamReader(inputStream, "gbk"));
                String line = null;
                while ((line = bufferedReader.readLine()) != null) {
                    sb.append(line);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (bufferedReader != null) {
                    try {
                        bufferedReader.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            action.call(sb.toString());
        }).start();
    }


    interface InfoAction {
        void call(String result);
    }


    ;
}
