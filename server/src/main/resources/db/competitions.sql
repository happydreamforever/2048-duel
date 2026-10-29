CREATE TABLE IF NOT EXISTS wallets (
 user_id VARCHAR(16) PRIMARY KEY,score INT NOT NULL DEFAULT 0,coins INT NOT NULL DEFAULT 100,
 revision BIGINT NOT NULL DEFAULT 0,updated_at BIGINT NOT NULL,FOREIGN KEY(user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS wallet_transactions (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id VARCHAR(16) NOT NULL,score_delta INT NOT NULL,
 coin_delta INT NOT NULL,reason VARCHAR(255) NOT NULL,actor_id CHAR(32) NULL,created_at BIGINT NOT NULL,
 FOREIGN KEY(user_id) REFERENCES users(id),KEY idx_wallet_user(user_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS competition_operations (
 operation_key VARCHAR(160) PRIMARY KEY, created_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS competition_entries (
 room_id VARCHAR(100) NOT NULL,user_id VARCHAR(16) NOT NULL,game VARCHAR(20) NOT NULL,
 mode VARCHAR(16) NOT NULL,fee INT NOT NULL,status VARCHAR(16) NOT NULL DEFAULT 'entered',
 created_at BIGINT NOT NULL,PRIMARY KEY(room_id,user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS competition_rooms (
 id VARCHAR(100) PRIMARY KEY,game VARCHAR(20) NOT NULL,mode VARCHAR(16) NOT NULL,
 status VARCHAR(16) NOT NULL,host VARCHAR(16) NOT NULL,opponent VARCHAR(16) NOT NULL,
 score_a INT NOT NULL DEFAULT 0,score_b INT NOT NULL DEFAULT 0,winner_name VARCHAR(16) NULL,
 payload LONGTEXT NOT NULL,updated_at BIGINT NOT NULL,KEY idx_competition_game(game,status,updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS coin_purchase_orders (
 id VARCHAR(100) PRIMARY KEY,user_id VARCHAR(16) NOT NULL,coins INT NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'pending',payment_reference VARCHAR(255) NOT NULL DEFAULT '',
 admin_note VARCHAR(255) NOT NULL DEFAULT '',created_at BIGINT NOT NULL,updated_at BIGINT NOT NULL,
 FOREIGN KEY(user_id) REFERENCES users(id),KEY idx_purchases_status(status,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS game_saves (
 user_id VARCHAR(16) NOT NULL,save_id VARCHAR(100) NOT NULL,revision BIGINT NOT NULL,
 game VARCHAR(16) NOT NULL,mode VARCHAR(16) NOT NULL,completed BOOLEAN NOT NULL,deleted BOOLEAN NOT NULL,
 payload LONGTEXT NOT NULL,updated_at BIGINT NOT NULL,PRIMARY KEY(user_id,save_id),
 FOREIGN KEY(user_id) REFERENCES users(id),KEY idx_saves_updated(updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
