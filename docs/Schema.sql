-- users  
create table users (  
  id uuid primary key,  
  username text unique not null,  
  display_name text,  
  phone text unique,  
  email text unique,  
  password_hash text not null,  
  avatar_media_id uuid,  
  bio text,  
  created_at timestamptz default now(),  
  updated_at timestamptz default now(),  
  last_seen_at timestamptz  
);  
  
create index idx_users_last_seen on users (last_seen_at);  
alter table users  
  add constraint users_avatar_media_fkey foreign key (avatar_media_id) references media (id);  
  
-- media  
create table media (  
  id uuid primary key,  
  owner_id uuid,  
  storage_key text,  
  url text,  
  mime_type text,  
  file_name text,  
  file_size int8,  
  width int4,  
  height int4,  
  duration_seconds numeric,  
  checksum text,  
  uploaded_at timestamptz default now(),  
  metadata jsonb  
);  
  
create index idx_media_owner on media (owner_id);  
alter table media  
  add constraint media_owner_fkey foreign key (owner_id) references users (id);  
  
-- contacts (pk owner_id, contact_id)  
create table contacts (  
  owner_id uuid not null,  
  contact_id uuid not null,  
  is_muted boolean not null default false,  
  is_blocked boolean not null default false,  
  alias text,  
  created_at timestamptz default now(),  
  primary key (owner_id, contact_id)  
);  
  
alter table contacts  
  add constraint contacts_owner_fkey foreign key (owner_id) references users (id),  
  add constraint contacts_contact_fkey foreign key (contact_id) references users (id);  
  
-- conversations  
create table conversations (  
  id uuid primary key,  
  type text not null, -- e.g., 'private','group','channel'  
  title text,  
  description text,  
  avatar_media_id uuid,  
  is_public boolean not null default false,  
  username text unique,  
  linked_discussion_group_id uuid unique,  
  metadata jsonb,  
  created_by uuid,  
  created_at timestamptz default now(),  
  updated_at timestamptz default now()  
);  
  
create index idx_conversations_created_by on conversations (created_by);  
alter table conversations  
  add constraint conversations_avatar_media_fkey foreign key (avatar_media_id) references media (id),  
  add constraint conversations_created_by_fkey foreign key (created_by) references users (id),  
  add constraint conversations_linked_group_fkey foreign key (linked_discussion_group_id) references conversations (id);  
  
-- conversation_members (pk conversation_id, user_id)  
create table conversation_members (  
  conversation_id uuid not null,  
  user_id uuid not null,  
  role text not null default 'MEMBER',  
  joined_at timestamptz default now(),  
  left_at timestamptz,  
  is_muted boolean not null default false,  
  settings jsonb,  
  primary key (conversation_id, user_id)  
);  
  
create index idx_conversation_members_user on conversation_members (user_id);  
create index idx_conversation_members_active on conversation_members (conversation_id) where left_at is null;  
alter table conversation_members  
  add constraint conversation_members_conv_fkey foreign key (conversation_id) references conversations (id),  
  add constraint conversation_members_user_fkey foreign key (user_id) references users (id),
  add constraint conversation_members_role_check check (role in ('OWNER', 'ADMIN', 'MEMBER'));  
  
-- messages (append-only). id uses identity (monotonic).  
create table messages (  
  id int8 primary key generated always as identity,  
  conversation_id uuid not null,  
  sender_id uuid,  
  created_at timestamptz default now(),  
  edited_at timestamptz,  
  deleted boolean not null default false,  
  reply_to_message_id int8,  
  forwarded_from_user_id uuid,  
  forwarded_from_conversation_id uuid,  
  forwarded_at timestamptz,  
  message_type text not null default 'text',  
  body text,  
  body_json jsonb,  
  metadata jsonb  
);  
  
-- essential indexes for keyset pagination and common access patterns  
create index idx_messages_conv_id on messages (conversation_id, id desc);  
create index idx_messages_conv_created_at on messages (conversation_id, created_at desc);  
create index idx_messages_conv_not_deleted on messages (conversation_id, id desc) where deleted = false;  
  
alter table messages  
  add constraint messages_conv_fkey foreign key (conversation_id) references conversations (id),  
  add constraint messages_sender_fkey foreign key (sender_id) references users (id),  
  add constraint messages_reply_fkey foreign key (reply_to_message_id) references messages (id),  
  add constraint messages_forwarded_conv_fkey foreign key (forwarded_from_conversation_id) references conversations (id),  
  add constraint messages_forwarded_user_fkey foreign key (forwarded_from_user_id) references users (id);  
  
-- message_media (pk message_id, media_id)  
create table message_media (  
  message_id int8 not null,  
  media_id uuid not null,  
  ordinal int4 not null default 0,  
  primary key (message_id, media_id)  
);  
create index idx_message_media_message on message_media (message_id, ordinal);  
alter table message_media  
  add constraint message_media_message_fkey foreign key (message_id) references messages (id),  
  add constraint message_media_media_fkey foreign key (media_id) references media (id);  
  
-- message_reactions (pk message_id, user_id, reaction)  
create table message_reactions (  
  message_id int8 not null,  
  user_id uuid not null,  
  reaction text not null,  
  reacted_at timestamptz default now(),  
  primary key (message_id, user_id, reaction)  
);  
create index idx_message_reactions_message on message_reactions (message_id);  
alter table message_reactions  
  add constraint message_reactions_message_fkey foreign key (message_id) references messages (id),  
  add constraint message_reactions_user_fkey foreign key (user_id) references users (id);  
  
-- pinned_messages (pk conversation_id, message_id)  
create table pinned_messages (  
  conversation_id uuid not null,  
  message_id int8 not null,  
  pinned_by uuid,  
  pinned_at timestamptz default now(),  
  primary key (conversation_id, message_id)  
);  
create index idx_pinned_messages_conv on pinned_messages (conversation_id, pinned_at desc);  
alter table pinned_messages  
  add constraint pinned_messages_conv_fkey foreign key (conversation_id) references conversations (id),  
  add constraint pinned_messages_message_fkey foreign key (message_id) references messages (id),  
  add constraint pinned_messages_by_fkey foreign key (pinned_by) references users (id);  
  
-- conversation_invite_links  
create table conversation_invite_links (  
  id bigserial primary key,  
  conversation_id uuid not null references conversations(id) on delete cascade,  
  created_by uuid not null references users(id),  
  
  invite_code text not null unique,  
  name text,  
  
  expire_at timestamptz,  
  member_limit int default 0,  
  current_uses int default 0,  
  
  is_revoked boolean default false,  
  is_primary boolean default false,  
  
  created_at timestamptz default now(),  
  updated_at timestamptz default now()  
);  
  
create unique index idx_invite_code on conversation_invite_links(invite_code);  
create index idx_conversation_invite_links on conversation_invite_links(conversation_id);  
create index idx_invite_links_primary on conversation_invite_links(conversation_id, is_primary);  

  
-- unread_counters simplified: only last_read message per user per conversation  
create table unread_counters (  
  conversation_id uuid not null,  
  user_id uuid not null,  
  last_read_message_id int8,  
  updated_at timestamptz default now(),  
  primary key (conversation_id, user_id)  
);  
create index idx_unread_user on unread_counters (user_id);  
alter table unread_counters  
  add constraint unread_counters_conv_fkey foreign key (conversation_id) references conversations (id),  
  add constraint unread_counters_user_fkey foreign key (user_id) references users (id),  
  add constraint unread_counters_last_read_fkey foreign key (last_read_message_id) references messages (id);  
  
-- message_delivery_status: optional/archival. keep only if required for compliance.  
-- recommended: keep this table small / TTL'd or route writes to a different storage (redis).  
create table message_delivery_status (  
  message_id int8 not null,  
  user_id uuid not null,  
  delivered_at timestamptz,  
  read_at timestamptz,  
  state text,  
  client_info jsonb,  
  primary key (message_id, user_id)  
);  
create index idx_mds_message_user on message_delivery_status (message_id, user_id);  
create index idx_mds_user_unread on message_delivery_status (user_id) where read_at is null;  
alter table message_delivery_status  
  add constraint mds_message_fkey foreign key (message_id) references messages (id),  
  add constraint mds_user_fkey foreign key (user_id) references users (id);  
  
-- small operational projection table for fast conversation list reads (maintained asynchronously)  
create table conversation_list (  
  user_id uuid not null,  
  conversation_id uuid not null,  
  last_message_id int8,  
  last_message_preview text,  
  unread_count int4 default 0,  
  last_updated timestamptz default now(),  
  primary key (user_id, conversation_id)  
);  
create index idx_conversation_list_user on conversation_list (user_id);  
alter table conversation_list  
  add constraint conversation_list_user_fkey foreign key (user_id) references users (id),  
  add constraint conversation_list_conv_fkey foreign key (conversation_id) references conversations (id);  
  
-- minimal maintenance / helper indexes  
create index idx_messages_deleted on messages (deleted) where deleted = true;  
create index idx_messages_sender on messages (sender_id);  
  
-- message_post_views (pk message_id)
create table message_post_views (
  message_id int8 primary key references messages(id) on delete cascade,
  view_count int8 not null default 0,
  updated_at timestamptz default now()
);

-- discussion_thread_links (1 channel post <-> 1 group thread root)
create table discussion_thread_links (
  id uuid primary key,
  channel_post_message_id int8 not null unique references messages(id) on delete cascade,
  group_root_message_id int8 not null unique references messages(id) on delete cascade,
  comment_count int4 not null default 0,
  created_at timestamptz default now()
);
create index idx_dtl_channel_post on discussion_thread_links(channel_post_message_id);
create index idx_dtl_group_root on discussion_thread_links(group_root_message_id);

-- example autovacuum tuning for high-update candidate tables  
alter table unread_counters set (autovacuum_vacuum_scale_factor = 0.01, autovacuum_vacuum_threshold = 50);  
alter table message_delivery_status set (autovacuum_vacuum_scale_factor = 0.02, autovacuum_vacuum_threshold = 100);  
alter table message_post_views set (autovacuum_vacuum_scale_factor = 0.02, autovacuum_vacuum_threshold = 100);  

-- recommended constraints & defaults enforced  
alter table messages alter column message_type set default 'text';  
alter table messages alter column deleted set not null;  
  
-- note: consider creating partitions later (hash on conversation_id) when messages grow large.  