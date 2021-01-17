package client;

import connection.Message;
import connection.MessageType;
import connection.Network;
import database.SQLService;
import sound.MakeSound;
import validator.Validator;

import java.io.IOException;
import java.net.Socket;
import java.sql.SQLException;

/**
 * @author Zurbaevi Nika
 */
public class ClientGuiController {
    private Network connection;
    private ClientGuiModel model;
    private ClientGuiView view;

    private volatile boolean clientConnected;
    private String nickname;
    private boolean isDatabaseConnected;

    public void run(ClientGuiController clientGuiController) {
        model = new ClientGuiModel();
        view = new ClientGuiView(clientGuiController);
        view.initComponents();
        while (true) {
            if (clientGuiController.isClientConnected()) {
                clientGuiController.userNameRegistration();
                clientGuiController.receiveMessageFromServer();
                clientGuiController.setClientConnected(false);
            }
        }
    }

    protected void userNameRegistration() {
        while (true) {
            try {
                Message message = connection.receive();
                if (message.getTypeMessage() == MessageType.REQUEST_NAME_USER) {
                    nickname = SQLService.getNickname(nickname);
                    connection.send(new Message(MessageType.USER_NAME, nickname));
                }
                if (message.getTypeMessage() == MessageType.NAME_USED) {
                    view.errorDialogWindow("A user with this name is already in the chat");
                    disableClient();
                    break;
                }
                if (message.getTypeMessage() == MessageType.NAME_ACCEPTED) {
                    view.addMessage(String.format("Your name is accepted (%s)\n", nickname));
                    model.setUsers(message.getListUsers());
                    break;
                }
            } catch (Exception e) {
                e.printStackTrace();
                view.errorDialogWindow("An error occurred while registering the name. Try reconnecting");
                try {
                    connection.close();
                    clientConnected = false;
                    break;
                } catch (IOException ex) {
                    view.errorDialogWindow("Error closing connection");
                }
            }
        }
    }

    protected void receiveMessageFromServer() {
        while (clientConnected) {
            try {
                Message message = connection.receive();
                if (message.getTypeMessage() == MessageType.TEXT_MESSAGE) {
                    processIncomingMessage(message);
                }
                if (message.getTypeMessage() == MessageType.USERNAME_CHANGED) {
                    notifyNicknameChanged(message);
                }
                if (message.getTypeMessage() == MessageType.PRIVATE_TEXT_MESSAGE) {
                    processingOfPrivateMessagesForSending(message);
                }
                if (message.getTypeMessage() == MessageType.USER_ADDED) {
                    informAboutAddingNewUser(message);
                }
                if (message.getTypeMessage() == MessageType.REMOVED_USER) {
                    informAboutDeletingNewUser(message);
                }
            } catch (Exception e) {
                view.errorDialogWindow("An error occurred while receiving a message from the server.");
                setClientConnected(false);
                view.refreshListUsers(model.getAllNickname());
                break;
            }
        }
    }

    public boolean isClientConnected() {
        return clientConnected;
    }

    public void setClientConnected(boolean clientConnected) {
        this.clientConnected = clientConnected;
    }

    protected void informAboutAddingNewUser(Message message) {
        model.addUser(message.getTextMessage());
        MakeSound.playSound("connected.wav");
        view.refreshListUsers(model.getAllNickname());
        view.addMessage(String.format("(%s) has joined the chat.\n", message.getTextMessage()));
    }

    protected void informAboutDeletingNewUser(Message message) {
        model.deleteUser(message.getTextMessage());
        MakeSound.playSound("disconnected.wav");
        view.refreshListUsers(model.getAllNickname());
        view.addMessage(String.format("(%s) has left the chat.\n", message.getTextMessage()));
    }

    protected void processingOfPrivateMessagesForSending(Message message) {
        String[] data = message.getTextMessage().split(" ");
        StringBuilder formattingForSendingPrivateMessage = new StringBuilder();
        for (int i = 1; i < data.length - 1; i++) {
            formattingForSendingPrivateMessage.append(data[i]).append(" ");
        }
        view.addMessage(String.format("Private message from (%s): %s\n", data[data.length - 1], formattingForSendingPrivateMessage.toString()));
    }

    protected void notifyNicknameChanged(Message message) {
        String[] data = message.getTextMessage().split(" ");
        view.addMessage(message.getTextMessage() + "\n");
        model.deleteUser(data[0]);
        model.addUser(data[data.length - 1]);
        view.refreshListUsers(model.getAllNickname());
    }

    protected void processIncomingMessage(Message message) {
        view.addMessage(message.getTextMessage());
    }

    protected void disableClient() {
        try {
            if (clientConnected) {
                connection.send(new Message(MessageType.DISABLE_USER));
                model.getAllNickname().clear();
                clientConnected = false;
                view.refreshListUsers(model.getAllNickname());
                view.addMessage("You have disconnected from the server.\n");
            } else {
                view.errorDialogWindow("You are already disconnected.");
            }
        } catch (Exception e) {
            view.errorDialogWindow("An error occurred while disconnecting.");
        }
    }

    protected void connectToServer() {
        if (!clientConnected) {
            while (true) {
                try {
                    connection = new Network(new Socket(view.getServerAddress(), view.getPort()));
                    clientConnected = true;
                    view.addMessage("You have connected to the server.\n");
                    break;
                } catch (Exception e) {
                    view.errorDialogWindow("An error has occurred! Perhaps you entered the wrong server address or port. try again");
                    break;
                }
            }
        } else {
            view.errorDialogWindow("You are already connected!");
        }
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    protected void sendMessageOnServer(String text) {
        try {
            connection.send(new Message(MessageType.TEXT_MESSAGE, text));
        } catch (Exception e) {
            view.errorDialogWindow("Error sending message");
        }
    }

    protected void sendPrivateMessageOnServer(String... data) {
        try {
            if (!nickname.equals(data[0])) {
                view.addMessage(String.format("Private message sent to user (%s)\n", data[0]));
                connection.send(new Message(MessageType.PRIVATE_TEXT_MESSAGE, data));
            } else {
                view.errorDialogWindow("You cannot send a private message to yourself");
            }
        } catch (Exception e) {
            view.errorDialogWindow("Error sending message");
        }
    }

    public void changeNickname() {
        String newNickname = view.getNickname();
        try {
            if (Validator.isValidChangeNickname(newNickname) && SQLService.changeNick(nickname, newNickname)) {
                model.deleteUser(nickname);
                nickname = newNickname;
                model.addUser(newNickname);
                view.refreshListUsers(model.getAllNickname());
                try {
                    connection.send(new Message(MessageType.USERNAME_CHANGED, newNickname));
                } catch (IOException e) {
                    view.errorDialogWindow(e.getMessage());
                }
            } else {
                view.errorDialogWindow("Enter correct data");
            }
        } catch (SQLException sqlException) {
            view.errorDialogWindow(sqlException.getMessage());
        }
    }

    public boolean isDatabaseConnected() {
        return isDatabaseConnected;
    }

    public void setDatabaseConnected(boolean databaseConnected) {
        isDatabaseConnected = databaseConnected;
    }
}
