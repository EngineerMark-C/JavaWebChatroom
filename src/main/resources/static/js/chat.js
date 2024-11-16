let socket;
let currentUser = '';
let onlineUsers = new Set();

function startClient() {
    const username = document.getElementById('username').value.trim();
    const password = document.getElementById('password').value.trim();

    if (!username || !password) {
        alert('用户名和密码不能为空');
        return;
    }

    // 连接到WebSocket代理
    socket = new WebSocket('ws://localhost:8080');

    socket.onopen = function() {
        console.log('连接到服务器');
        // 发送登录信息，使用JSON格式
        const loginMessage = JSON.stringify({
            type: 'login',
            username: username,
            password: password
        });
        socket.send(loginMessage);
    };

    socket.onmessage = function(event) {
        const message = event.data;
        console.log('收到消息:', message);
8
        try {
            const response = JSON.parse(message);
            
            // 避免重复处理相同的聊天消息
            if (response.type === 'chat' && response.sender === currentUser && response.receiver !== 'all') {
                // 只处理私聊的确认消息
                displayMessage(response);
            } else {
                // 处理其他所有消息
                switch(response.type) {
                    case 'system':
                        if (response.content.includes('登录成功')) {
                            currentUser = username;
                            document.getElementById('currentUser').textContent = username;
                            showChatPanel();
                        }
                        displayMessage(response);
                        break;
                    case 'error':
                        alert(response.content);
                        break;
                    case 'chat':
                    case 'history':
                        displayMessage(response);
                        break;
                    case 'online_users':
                        updateOnlineUsers(response.users);
                        break;
                }
            }
        } catch (e) {
            console.error('解析服务器消息时发生错误:', e);
            console.log('原始消息:', message);
        }
    };

    socket.onclose = function() {
        console.log('连接关闭');
        alert('与服务器的连接已断开');
    };

    socket.onerror = function(error) {
        console.error('WebSocket错误:', error);
        alert('连接错误，请重试');
    };
}

function updateOnlineUsers(users) {
    onlineUsers = new Set(users);
    const userList = document.getElementById('userList');
    const receiverSelect = document.getElementById('receiverSelect');
    
    // 更新侧边栏用户列表
    userList.innerHTML = '';
    users.forEach(user => {
        if (user !== currentUser) {
            const li = document.createElement('li');
            li.textContent = user;
            li.onclick = () => setReceiver(user);
            userList.appendChild(li);
        }
    });
    
    // 更新接收者下拉列表
    receiverSelect.innerHTML = '<option value="all">所有人</option>';
    users.forEach(user => {
        if (user !== currentUser) {
            const option = document.createElement('option');
            option.value = user;
            option.textContent = user;
            receiverSelect.appendChild(option);
        }
    });
}

// 设置接收者的函数
function setReceiver(username) {
    document.getElementById('receiverSelect').value = username;
}
function sendMessage() {
    const messageInput = document.getElementById('messageInput');
    const receiverSelect = document.getElementById('receiverSelect');
    const messageContent = messageInput.value.trim();
    const receiver = receiverSelect.value;

    if (!messageContent) {
        return;
    }

    const chatMessage = {
        type: 'chat',
        sender: currentUser,
        receiver: receiver,
        content: messageContent
    };

    socket.send(JSON.stringify(chatMessage));
    messageInput.value = '';
}

function displayMessage(message) {
    const messageArea = document.getElementById('messageArea');
    const messageDiv = document.createElement('div');
    
    // 确定消息类型和样式
    let type = 'received';
    
    // 如果消息发送者是当前用户，设置为发送样式
    if (message.sender === currentUser) {
        type = 'sent';
    }
    
    // 如果是系统消息，设置为系统样式
    if (message.type === 'system') {
        type = 'system';
    }
    
    // 设置消息样式
    messageDiv.className = `message ${type}`;
    
    // 构建消息HTML
    let html = '';
    
    // 添加时间戳
    if (message.timestamp) {
        html += `<div class="timestamp">[${message.timestamp}]</div>`;
    }
    
    // 添加发送者名称（如果不是系统消息且不是自己发送的）
    if (message.sender && type === 'received') {
        html += `<div class="sender-name">${message.sender}</div>`;
    }
    
    // 添加消息内容
    html += `<div class="message-content">${message.content}</div>`;
    
    messageDiv.innerHTML = html;
    messageArea.appendChild(messageDiv);
    
    // 滚动到最新消息
    messageArea.scrollTop = messageArea.scrollHeight;
}

function register() {
    const username = document.getElementById('regUsername').value.trim();
    const password = document.getElementById('regPassword').value.trim();
    const confirmPassword = document.getElementById('confirmPassword').value.trim();
    
    if (!username || !password || !confirmPassword) {
        alert('所有字段都必须填写');
        return;
    }
    
    if (password !== confirmPassword) {
        alert('两次输入的密码不一致');
        return;
    }

    // 连接到服务器进行注册
    const socket = new WebSocket('ws://localhost:8080');
    
    socket.onopen = function() {
        const registerMessage = JSON.stringify({
            type: 'register',
            username: username,
            password: password
        });
        socket.send(registerMessage);
    };

    socket.onmessage = function(event) {
        const message = event.data;
        alert(message);
        if (message.includes('注册成功')) {
            showLogin();
        }
        socket.close();
    };
}

// 界面切换函数
function showLogin() {
    document.getElementById('loginPanel').style.display = 'flex';
    document.getElementById('registerPanel').style.display = 'none';
    document.getElementById('chatPanel').style.display = 'none';
}

function showRegister() {
    document.getElementById('loginPanel').style.display = 'none';
    document.getElementById('registerPanel').style.display = 'flex';
    document.getElementById('chatPanel').style.display = 'none';
}

function showChatPanel() {
    document.getElementById('loginPanel').style.display = 'none';
    document.getElementById('registerPanel').style.display = 'none';
    document.getElementById('chatPanel').style.display = 'flex';
}

// 添加回车键发送消息的支持
document.addEventListener('DOMContentLoaded', function() {
    const messageInput = document.getElementById('messageInput');
    if (messageInput) {
        messageInput.addEventListener('keypress', function(e) {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                sendMessage();
            }
        });
    }
});