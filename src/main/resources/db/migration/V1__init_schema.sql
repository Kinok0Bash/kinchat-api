create table if not exists users_auth (
    user_id uuid primary key,
    login varchar(32) not null,
    login_lower varchar(32) not null unique,
    password_hash varchar(100) not null,
    created_at timestamp with time zone not null default current_timestamp
);

create table if not exists users_profile (
    user_id uuid primary key,
    first_name varchar(60) not null,
    last_name varchar(60) not null,
    avatar_url varchar(512) not null,
    avatar_object_key varchar(512),
    updated_at timestamp with time zone not null default current_timestamp
);

create table if not exists chats (
    chat_id uuid primary key,
    chat_type varchar(16) not null,
    direct_key varchar(73) unique,
    title varchar(255),
    avatar_url varchar(512),
    created_by_user_id uuid,
    created_at timestamp with time zone not null default current_timestamp
);

create table if not exists chat_members (
    chat_id uuid not null,
    user_id uuid not null,
    joined_at timestamp with time zone not null default current_timestamp,
    primary key (chat_id, user_id)
);

create table if not exists messages (
    message_id uuid primary key,
    chat_id uuid not null,
    sender_user_id uuid not null,
    client_message_id uuid,
    text text,
    created_at timestamp with time zone not null default current_timestamp
);

create table if not exists message_attachments (
    message_id uuid not null,
    attachment_no integer not null,
    object_key varchar(512) not null,
    file_name varchar(255) not null,
    content_type varchar(255) not null,
    size_bytes bigint not null,
    url varchar(512) not null,
    created_at timestamp with time zone not null default current_timestamp,
    primary key (message_id, attachment_no)
);

create table if not exists ws_tickets (
    ticket uuid primary key,
    user_id uuid not null,
    expires_at timestamp with time zone not null,
    used_at timestamp with time zone
);

create index if not exists idx_chat_members_user_id on chat_members (user_id, chat_id);
create index if not exists idx_chats_direct_key on chats (direct_key);
create index if not exists idx_messages_chat_created_at on messages (chat_id, created_at desc, message_id desc);
create unique index if not exists idx_messages_sender_chat_client_message on messages (sender_user_id, chat_id, client_message_id);
create index if not exists idx_message_attachments_message_id on message_attachments (message_id, attachment_no);
create index if not exists idx_ws_tickets_user_id on ws_tickets (user_id);
create index if not exists idx_ws_tickets_expires_at on ws_tickets (expires_at);
