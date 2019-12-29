package gb.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class ClientHandler {

    private int TIME_OUT = 120;

    private Server server;
    private Socket socket;
    private DataOutputStream out;
    private DataInputStream in;
    private List<String> blackList;


    private long a;

    public String getNick() {
        return nick;
    }

    String nick;

    public ClientHandler(Server server, Socket socket) {
        try {
            this.socket = socket;
            this.server = server;
            this.in = new DataInputStream(socket.getInputStream());
            this.out = new DataOutputStream(socket.getOutputStream());
            this.blackList = new ArrayList<>();
            new Thread(() -> {
                try {
                    while (true) {
                        String str = in.readUTF();
                        if(str.startsWith("/addUser")) {
                            String[] tokens = str.split(" ");
                            if (AuthService.validLoginNick("login", tokens[1])) {
                                if (AuthService.validLoginNick("nickname", tokens[3])) {
                                    if ((AuthService.passwordVerification( tokens[2]))) {
                                        AuthService.addUser(tokens[1], tokens[2], tokens[3]);
                                        sendMsg("/clearRegFields");
                                        sendMsg("Регистрация прошла успешно");
                                    } else {
                                        sendMsg("Некорректный пароль");
                                    }

                                } else {
                                    sendMsg("Некорректный или занятый ник");
                                }

                            } else {
                                sendMsg("Некорректный или занятый логин");
                            }

                        }
                        if(str.startsWith("/auth")) {
                            String[] tokens = str.split(" ");
                            String newNick = AuthService.getNickByLoginAndPass(tokens[1], tokens[2]);

                            if(newNick != null) {
                                if(!server.isNickBusy(newNick)) {
                                    sendMsg("/authok");
                                    nick = newNick;
                                    server.subscribe(this);
                                    blackList = AuthService.blackListFromSQL(nick);
                                    break;
                                } else {
                                    sendMsg("Учетная запись уже используется");
                                }
                            } else {
                                sendMsg("Неверный логин/пароль");
                            }
                        }
                    }
                    a = System.currentTimeMillis()/1000;
                    // Поток для отключения по таймауту
                    new Thread(new Runnable() {
                        @Override
                        public void run() {

                            while (true) {
                                try {
                                    Thread.sleep(1000);
                                    if (System.currentTimeMillis()/1000 - a >= TIME_OUT) {
                                        sendMsg("Вы отключены по таймауту");
                                        out.writeUTF("/timeout");
                                        break;
                                    }
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
//                                // Строка для тестового отображения отсчета времени
//                                System.out.println(System.currentTimeMillis()/1000 - a + " " + nick);
                            }
                        }
                    }).start();
                    while (true) {
                        String str = in.readUTF();

                        if(str.startsWith("/")){
                            if (str.equals("/end")) {
                                out.writeUTF("/serverclosed");
                                break;
                            }
                            if(str.startsWith("/w ")) {
                                String[] tokens = str.split(" ", 3);
                                server.sendPersonalMsg(this, tokens[1], tokens[2]);
                            }

                            if(str.startsWith("/blackList ")){
                                String[] tokens = str.split(" ");
                                if (blackList.contains(tokens[1])) {
                                    sendMsg("Пользователь " + tokens[1] + " уже внесен в черный список");
                                } else {
                                    blackList.add(tokens[1]);
                                    AuthService.addBlackListSQL(nick, tokens[1]);
                                    sendMsg("Вы добавили пользователя " + tokens[1] + " в черный список");
                                }

                            }
                            if (str.startsWith("/unBlackList ")) {
                                String[] tokens = str.split(" ");
                                if (blackList.contains(tokens[1])) {
                                    blackList.remove(tokens[1]);
                                    AuthService.removeBlackListSQL(nick, tokens[1]);
                                    sendMsg("Пользователь " + tokens[1] + " удален из черного список");
                                } else {
                                    sendMsg("Пользователя " + tokens[1] + " нет в черном списке");
                                }
                            }
                        } else {
                            server.broadcastMsg(this,nick + " " + str);
                        }
                        System.out.println("Client: " + str);
                        a = System.currentTimeMillis()/1000;

                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    try {
                        in.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    try {
                        out.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    try {
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    server.unsubscribe(this);
                }
            }).start();


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendMsg(String msg) {
        try {
            out.writeUTF(msg);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean checkBlackList(String nick) {
        return blackList.contains(nick);
    }



}
