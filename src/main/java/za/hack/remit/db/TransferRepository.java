package za.hack.remit.db;

import za.hack.remit.transfer.Transfer;
import za.hack.remit.transfer.TransferStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TransferRepository {

    public void save(Transfer transfer) {
        String sql = "INSERT INTO transfers (reference, session_id, sender_phone, recipient_phone, recipient_name, " +
                "amount_zar, fee_zar, total_zar, receive_usd, exchange_rate, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, transfer.getReference());
            ps.setString(2, transfer.getSessionId());
            ps.setString(3, transfer.getSenderPhone());
            ps.setString(4, transfer.getRecipientPhone());
            ps.setString(5, transfer.getRecipientName());

            // Passed directly because they are ALREADY BigDecimals:
            ps.setBigDecimal(6, transfer.getAmountZar());
            ps.setBigDecimal(7, transfer.getFeeZar());
            ps.setBigDecimal(8, transfer.getTotalZar());
            ps.setBigDecimal(9, transfer.getReceiveAmountUsd());
            ps.setBigDecimal(10, transfer.getRate());

            ps.setString(11, transfer.getStatus().name());

            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Optional<Transfer> findByReference(String reference) {
        String sql = "SELECT * FROM transfers WHERE reference = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, reference);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToTransfer(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    public List<Transfer> findAll() {
        List<Transfer> list = new ArrayList<>();
        String sql = "SELECT * FROM transfers ORDER BY created_at DESC";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                list.add(mapResultSetToTransfer(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public void updateStatus(String reference, TransferStatus status) {
        String sql = "UPDATE transfers SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE reference = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status.name());
            ps.setString(2, reference);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private Transfer mapResultSetToTransfer(ResultSet rs) throws SQLException {
        // Constructing Transfer directly using BigDecimals returned from ResultSet:
        return new Transfer(
                rs.getString("reference"),
                rs.getString("session_id"),
                rs.getString("sender_phone"),
                rs.getString("recipient_name"),
                rs.getString("recipient_phone"),
                "en", // default language
                rs.getBigDecimal("amount_zar"),
                rs.getBigDecimal("fee_zar"),
                rs.getBigDecimal("total_zar"),
                rs.getBigDecimal("exchange_rate"),
                rs.getBigDecimal("receive_usd")
        );
    }
}