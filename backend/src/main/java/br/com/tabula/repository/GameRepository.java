package br.com.tabula.repository;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class GameRepository {
    public List<GameData> findAll(Connection connection) throws SQLException {
        List<GameData> games = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id,external_id,nome,descricao,cover_url,categoria,min_players,max_players,avg_play_time,complexity
                FROM jogos WHERE external_id IS NOT NULL ORDER BY nome,external_id
                """); ResultSet rows = statement.executeQuery()) {
            while (rows.next()) games.add(map(rows));
        }
        return games;
    }
    public Optional<GameData> find(Connection connection, String id, boolean lock) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id,external_id,nome,descricao,cover_url,categoria,min_players,max_players,avg_play_time,complexity
                FROM jogos WHERE external_id=?
                """ + (lock ? " FOR UPDATE" : ""))) {
            statement.setString(1,id); try (ResultSet rows=statement.executeQuery()) {
                return rows.next()?Optional.of(map(rows)):Optional.empty();
            }
        }
    }
    public GameData insert(Connection c,String id,String name,String description,String cover,String category,int min,int max,int avg,double complexity)throws SQLException{
        try(PreparedStatement s=c.prepareStatement("INSERT INTO jogos(external_id,nome,descricao,cover_url,categoria,min_players,max_players,avg_play_time,complexity,criado_em) VALUES(?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)")){
            bind(s,id,name,description,cover,category,min,max,avg,complexity);s.executeUpdate();
        } return find(c,id,false).orElseThrow();
    }
    public GameData update(Connection c,String id,String name,String description,String cover,String category,int min,int max,int avg,double complexity)throws SQLException{
        try(PreparedStatement s=c.prepareStatement("UPDATE jogos SET nome=?,descricao=?,cover_url=?,categoria=?,min_players=?,max_players=?,avg_play_time=?,complexity=?,atualizado_em=CURRENT_TIMESTAMP WHERE external_id=?")){
            s.setString(1,name);s.setString(2,description);s.setString(3,cover);s.setString(4,category);s.setInt(5,min);s.setInt(6,max);s.setInt(7,avg);s.setDouble(8,complexity);s.setString(9,id);if(s.executeUpdate()!=1)throw new SQLException("game_not_found");
        } return find(c,id,false).orElseThrow();
    }
    public boolean delete(Connection c,String id)throws SQLException{try(PreparedStatement s=c.prepareStatement("DELETE FROM jogos WHERE external_id=?")){s.setString(1,id);return s.executeUpdate()==1;}}
    public boolean isReferenced(Connection c,long id)throws SQLException{try(PreparedStatement s=c.prepareStatement("SELECT EXISTS(SELECT 1 FROM eventos WHERE jogo_id=? UNION ALL SELECT 1 FROM partidas WHERE jogo_id=? UNION ALL SELECT 1 FROM favoritos WHERE jogo_id=?)")){s.setLong(1,id);s.setLong(2,id);s.setLong(3,id);try(ResultSet r=s.executeQuery()){r.next();return r.getBoolean(1);}}}
    private static void bind(PreparedStatement s,String id,String n,String d,String cover,String cat,int min,int max,int avg,double comp)throws SQLException{s.setString(1,id);s.setString(2,n);s.setString(3,d);s.setString(4,cover);s.setString(5,cat);s.setInt(6,min);s.setInt(7,max);s.setInt(8,avg);s.setDouble(9,comp);}
    private static GameData map(ResultSet r)throws SQLException{return new GameData(r.getLong("id"),r.getString("external_id"),r.getString("nome"),r.getString("descricao"),r.getString("cover_url"),r.getString("categoria"),r.getInt("min_players"),r.getInt("max_players"),r.getInt("avg_play_time"),r.getDouble("complexity"));}
    public record GameData(long databaseId,String id,String name,String description,String coverUrl,String category,int minPlayers,int maxPlayers,int avgPlayTime,double complexity){}
}
