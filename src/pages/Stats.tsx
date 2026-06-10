import React, { useMemo, useState } from 'react';
import { useDatabase } from '../context/DatabaseContext';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { DownloadIcon, TrophyIcon, ZapIcon, PuzzleIcon } from '../components/Icons';
import { UserAvatar } from '../components/UserAvatar';

export const Stats: React.FC = () => {
  const { state } = useDatabase();
  const { isAdmin } = useAuth();
  const { showToast } = useToast();

  // 1. DATA PREPARATION FOR CHARTS & TABLES
  
  // Game popularity (plays)
  const gameStats = state.boardGames.map(game => {
    const plays = state.sessions.filter(s => s.gameId === game.id).length;
    return { name: game.name, plays };
  }).sort((a, b) => b.plays - a.plays);

  // Category distribution
  const categories = state.boardGames.reduce((acc, game) => {
    acc[game.category] = (acc[game.category] || 0) + 1;
    return acc;
  }, {} as Record<string, number>);

  const categoryData = Object.entries(categories).map(([name, count]) => ({
    name,
    count,
    percentage: Math.round((count / state.boardGames.length) * 100)
  }));

  // Month activity trend (last 6 months)
  // Group sessions by month (YYYY-MM)
  const monthlyActivity: Record<string, number> = {};
  
  // Initialize last 6 months with 0
  for (let i = 5; i >= 0; i--) {
    const d = new Date();
    d.setMonth(d.getMonth() - i);
    const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
    monthlyActivity[key] = 0;
  }

  state.sessions.forEach(s => {
    const key = s.date.substring(0, 7); // YYYY-MM
    if (key in monthlyActivity) {
      monthlyActivity[key]++;
    }
  });

  const monthLabels: Record<string, string> = {
    '01': 'Jan', '02': 'Fev', '03': 'Mar', '04': 'Abr', '05': 'Mai', '06': 'Jun',
    '07': 'Jul', '08': 'Ago', '09': 'Set', '10': 'Out', '11': 'Nov', '12': 'Dez'
  };

  const activityData = Object.entries(monthlyActivity).map(([key, plays]) => {
    const [yr, mo] = key.split('-');
    return {
      label: `${monthLabels[mo]} / ${yr.substring(2)}`,
      plays
    };
  });

  // Player Win Rankings
  const playerRankings = [...state.users].sort((a, b) => b.winCount - a.winCount);
  const [selectedGameFilter, setSelectedGameFilter] = useState('all');

  const advancedRanking = useMemo(() => {
    const filteredSessions = selectedGameFilter === 'all'
      ? state.sessions
      : state.sessions.filter(session => session.gameId === selectedGameFilter);

    return [...state.users]
      .map(user => {
        const plays = filteredSessions.filter(session => session.participantIds.includes(user.id)).length;
        const wins = filteredSessions.filter(session => session.winnerId === user.id).length;
        return { user, plays, wins };
      })
      .sort((a, b) => b.wins - a.wins || b.plays - a.plays);
  }, [selectedGameFilter, state.sessions, state.users]);

  const topWinner = advancedRanking[0];
  const gameFilterOptions = [{ id: 'all', name: 'Todos os jogos' }, ...state.boardGames.map(game => ({ id: game.id, name: game.name }))];

  // Game Ranking (plays + wins)
  const gameRankings = state.boardGames
    .map(game => {
      const plays = state.sessions.filter(s => s.gameId === game.id).length;
      const wins = state.sessions.filter(s => s.gameId === game.id && s.winnerId !== null).length;
      return { game, plays, wins };
    })
    .sort((a, b) => b.plays - a.plays || b.wins - a.wins);

  // Average session attendance
  const averageAttendance = state.sessions.length > 0 
    ? (state.sessions.reduce((acc, s) => acc + s.participantIds.length, 0) / state.sessions.length).toFixed(1)
    : '0';

  // 2. EXPORTS GENERATOR FUNCTIONS
  
  // CSV Export helper
  const handleExportCSV = () => {
    // Columns: Jogador, Partidas Jogadas, Vitórias, Taxa de Vitória
    let csvContent = '\uFEFF'; // UTF-8 BOM for Excel to open accents properly!
    csvContent += 'Ranking;Jogador;Partidas Jogadas;Vitórias;Taxa de Vitória (%)\r\n';

    playerRankings.forEach((user, idx) => {
      const plays = state.sessions.filter(s => s.participantIds.includes(user.id)).length;
      const winRate = plays > 0 ? Math.round((user.winCount / plays) * 100) : 0;
      csvContent += `${idx + 1};${user.name};${plays};${user.winCount};${winRate}\r\n`;
    });

    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.setAttribute('href', url);
    link.setAttribute('download', `tabula_relatorio_comunidade_${new Date().toISOString().substring(0, 10)}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);

    showToast('Relatório CSV exportado com sucesso!', 'success');
  };

  // Printable layout PDF trigger
  const handlePrintPDF = () => {
    window.print();
  };

  // 3. SVG CHART CALCULATIONS
  
  // Chart 1: Bar chart calculations
  const maxPlaysVal = Math.max(...gameStats.map(g => g.plays), 1);
  const barChartHeight = gameStats.length * 36 + 20;

  // Chart 3: Donut chart calculations
  let accumulatedPercent = 0;
  const donutRadius = 50;
  const donutCircumference = 2 * Math.PI * donutRadius; // 314.16
  const colors = ['#E06A47', '#2A6F60', '#F3B63F', '#3B7197', '#8E93A6'];

  return (
    <div className="container" style={{ paddingTop: '24px' }}>
      
      {/* Printable-only report header (uses print CSS from index.css) */}
      <div className="print-only-header">
        <h1 style={{ fontSize: '24pt', color: 'black' }}>TABULA BOARD GAME CLUB</h1>
        <h2 style={{ fontSize: '14pt', color: '#555', marginTop: '6px' }}>Relatório Consolidado de Desempenho e Atividade da Comunidade</h2>
        <p style={{ fontSize: '10pt', color: '#888', marginTop: '4px' }}>Gerado em: {new Date().toLocaleDateString('pt-BR')} • Status do Sistema: Ativo</p>
      </div>

      {/* Page Header */}
      <div style={headerSectionStyle} className="no-print">
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <ZapIcon size={32} style={{ color: 'var(--color-primary)' }} />
          <div>
            <h1 style={{ fontSize: '2rem', marginBottom: '4px' }}>Estatísticas & Relatórios</h1>
            <p style={{ color: 'var(--color-text-muted)' }}>Métricas de engajamento, popularidade de jogos e ranking de vitórias do clube.</p>
          </div>
        </div>
      </div>

      {/* Admin actions block */}
      {isAdmin && (
        <div className="card no-print" style={adminReportPanelStyle}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <PuzzleIcon size={20} style={{ color: 'var(--color-primary)' }} />
            <div>
              <h3 style={{ fontSize: '1rem', fontWeight: 700 }}>Painel de Exportação de Relatórios</h3>
              <p style={{ fontSize: '0.8rem', color: 'var(--color-text-muted)', marginTop: '2px' }}>
                Gerencie as métricas da comunidade. Exporte dados estruturados para planilhas ou imprima em PDF.
              </p>
            </div>
          </div>
          <div style={exportButtonsWrapperStyle}>
            <button className="btn btn-outline btn-sm" onClick={handleExportCSV}>
              <DownloadIcon size={14} /> Exportar Planilha (CSV / Excel)
            </button>
            <button className="btn btn-primary btn-sm" onClick={handlePrintPDF}>
              🖨️ Gerar Relatório PDF (Imprimir)
            </button>
          </div>
        </div>
      )}

      {/* Overall stats counters banner */}
      <div style={statsBannerGridStyle}>
        <div className="card" style={statBannerCardStyle}>
          <span style={statBannerTitleStyle}>Presença Média</span>
          <span style={statBannerValueStyle}>{averageAttendance} <span style={{ fontSize: '0.9rem', color: 'var(--color-text-muted)' }}>jogadores/mesa</span></span>
        </div>
        <div className="card" style={statBannerCardStyle}>
          <span style={statBannerTitleStyle}>Partidas / Mês</span>
          <span style={statBannerValueStyle}>
            {state.sessions.filter(s => {
              const d = new Date(s.date);
              const now = new Date();
              return d.getMonth() === now.getMonth() && d.getFullYear() === now.getFullYear();
            }).length}
            <span style={{ fontSize: '0.9rem', color: 'var(--color-text-muted)' }}> este mês</span>
          </span>
        </div>
        <div className="card" style={statBannerCardStyle}>
          <span style={statBannerTitleStyle}>Taxa de Engajamento</span>
          <span style={statBannerValueStyle}>
            {Math.round((state.users.filter(u => state.sessions.some(s => s.participantIds.includes(u.id))).length / state.users.length) * 100)}%
            <span style={{ fontSize: '0.9rem', color: 'var(--color-text-muted)' }}> de membros ativos</span>
          </span>
        </div>
      </div>

      {/* Visual Charts Section */}
      <div style={chartsGridStyle}>
        
        {/* Chart 1: Game Popularity (plays) */}
        <div className="card" style={chartCardStyle}>
          <h3 style={chartTitleStyle}>🔥 Jogos Mais Populares</h3>
          <p style={chartSubTitleStyle}>Número de partidas gravadas por jogo de mesa.</p>
          
          <div style={{ marginTop: '16px' }}>
            <svg width="100%" height={barChartHeight} viewBox={`0 0 500 ${barChartHeight}`} style={{ overflow: 'visible' }}>
              {gameStats.map((game, idx) => {
                const barWidth = (game.plays / maxPlaysVal) * 320;
                const yPos = idx * 36 + 10;
                return (
                  <g key={game.name}>
                    {/* Game Name */}
                    <text
                      x="100"
                      y={yPos + 16}
                      textAnchor="end"
                      style={{ fontSize: '0.75rem', fontWeight: 600, fill: 'var(--color-text-main)' }}
                    >
                      {game.name}
                    </text>
                    {/* Bar */}
                    <rect
                      x="110"
                      y={yPos}
                      width={Math.max(barWidth, 5)}
                      height="20"
                      rx="4"
                      fill="var(--color-primary)"
                      style={{ transition: 'width 0.5s ease-in-out' }}
                    />
                    {/* Value */}
                    <text
                      x={110 + barWidth + 10}
                      y={yPos + 15}
                      style={{ fontSize: '0.75rem', fontWeight: 700, fill: 'var(--color-text-muted)' }}
                    >
                      {game.plays} {game.plays === 1 ? 'partida' : 'partidas'}
                    </text>
                  </g>
                );
              })}
            </svg>
          </div>
        </div>

        {/* Chart 2: Monthly Activity Line Chart */}
        <div className="card" style={chartCardStyle}>
          <h3 style={chartTitleStyle}>📈 Atividade da Comunidade</h3>
          <p style={chartSubTitleStyle}>Evolução mensal do número de partidas jogadas.</p>
          
          <div style={{ marginTop: '20px', display: 'flex', justifyContent: 'center' }}>
            <svg width="100%" height="200" viewBox="0 0 450 200" style={{ overflow: 'visible' }}>
              {/* Grid lines */}
              <line x1="40" y1="20" x2="420" y2="20" stroke="#ECEBE6" strokeDasharray="4" />
              <line x1="40" y1="70" x2="420" y2="70" stroke="#ECEBE6" strokeDasharray="4" />
              <line x1="40" y1="120" x2="420" y2="120" stroke="#ECEBE6" strokeDasharray="4" />
              <line x1="40" y1="170" x2="420" y2="170" stroke="#ECEBE6" />

              {/* Draw points and polyline */}
              {(() => {
                const maxVal = Math.max(...activityData.map(d => d.plays), 1);
                const points = activityData.map((d, idx) => {
                  const x = 40 + idx * 75;
                  const y = 170 - (d.plays / maxVal) * 130;
                  return { x, y, label: d.label, val: d.plays };
                });

                const polylinePath = points.map(p => `${p.x},${p.y}`).join(' ');

                return (
                  <>
                    {/* Path line */}
                    {points.length > 1 && (
                      <polyline
                        fill="none"
                        stroke="var(--color-secondary)"
                        strokeWidth="3"
                        points={polylinePath}
                      />
                    )}
                    
                    {/* Points nodes */}
                    {points.map((p, idx) => (
                      <g key={idx}>
                        <circle
                          cx={p.x}
                          cy={p.y}
                          r="5"
                          fill="white"
                          stroke="var(--color-secondary)"
                          strokeWidth="3"
                        />
                        {/* Node value */}
                        <text
                          x={p.x}
                          y={p.y - 10}
                          textAnchor="middle"
                          style={{ fontSize: '0.7rem', fontWeight: 700, fill: 'var(--color-text-main)' }}
                        >
                          {p.val}
                        </text>
                        {/* Label X */}
                        <text
                          x={p.x}
                          y="188"
                          textAnchor="middle"
                          style={{ fontSize: '0.75rem', fill: 'var(--color-text-muted)' }}
                        >
                          {p.label}
                        </text>
                      </g>
                    ))}
                  </>
                );
              })()}
            </svg>
          </div>
        </div>

        {/* Chart 3: Categories Donut Chart */}
        <div className="card" style={chartCardStyle}>
          <h3 style={chartTitleStyle}>🧩 Distribuição por Categoria</h3>
          <p style={chartSubTitleStyle}>Composição temática dos jogos no acervo do clube.</p>
          
          <div style={donutFlexWrapperStyle}>
            <svg width="150" height="150" viewBox="0 0 120 120">
              <circle cx="60" cy="60" r={donutRadius} fill="none" stroke="#EBE9E4" strokeWidth="12" />
              {categoryData.map((cat, idx) => {
                const color = colors[idx % colors.length];
                const strokeDash = (cat.percentage / 100) * donutCircumference;
                const strokeOffset = donutCircumference - strokeDash + (accumulatedPercent / 100) * donutCircumference;
                
                // Keep track of accumulated percentage for offset calculation
                accumulatedPercent += cat.percentage;

                return (
                  <circle
                    key={cat.name}
                    cx="60"
                    cy="60"
                    r={donutRadius}
                    fill="none"
                    stroke={color}
                    strokeWidth="12"
                    strokeDasharray={`${strokeDash} ${donutCircumference}`}
                    strokeDashoffset={-strokeOffset}
                    transform="rotate(-90 60 60)" // Start drawing from top
                  />
                );
              })}
              {/* Centered label */}
              <text x="60" y="64" textAnchor="middle" style={{ fontSize: '0.55rem', fontWeight: 800, fill: 'var(--color-text-main)', fontFamily: 'var(--font-title)' }}>
                ACERVO
              </text>
            </svg>

            {/* Donut Legend */}
            <div style={donutLegendContainerStyle}>
              {categoryData.map((cat, idx) => (
                <div key={cat.name} style={legendRowStyle}>
                  <span style={{ ...colorIndicatorStyle, backgroundColor: colors[idx % colors.length] }} />
                  <span style={legendLabelTextStyle}>
                    <strong>{cat.percentage}%</strong> {cat.name} ({cat.count})
                  </span>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* Friendly Leaderboard Ranking list */}
        <div className="card" style={chartCardStyle}>
          <h3 style={{ ...chartTitleStyle, display: 'flex', alignItems: 'center', gap: '6px' }}>
            <TrophyIcon size={16} style={{ color: 'var(--color-accent)' }} />
            <span>Ranking de Vitórias Amigável</span>
          </h3>
          <p style={chartSubTitleStyle}>Os jogadores com mais partidas ganhas na comunidade.</p>
          
          <div style={rankingTableContainerStyle}>
            <table style={rankingTableStyle}>
              <thead>
                <tr style={tableHeaderStyle}>
                  <th style={thStyle}>Pos.</th>
                  <th style={thStyle}>Jogador</th>
                  <th style={{ ...thStyle, textAlign: 'center' }}>Partidas</th>
                  <th style={{ ...thStyle, textAlign: 'center' }}>Vitórias</th>
                </tr>
              </thead>
              <tbody>
                {playerRankings.slice(0, 5).map((user, idx) => {
                  const plays = state.sessions.filter(s => s.participantIds.includes(user.id)).length;
                  return (
                    <tr key={user.id} style={tableRowStyle}>
                      <td style={{ ...tdStyle, fontWeight: 700 }}>
                        {idx === 0 ? '1º' : idx === 1 ? '2º' : idx === 2 ? '3º' : `${idx + 1}`}
                      </td>
                      <td style={{ ...tdStyle, fontWeight: 600, display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <UserAvatar user={user} size={28} style={{ border: '1px solid var(--color-border)' }} />
                        <span>{user.name}</span>
                      </td>
                      <td style={{ ...tdStyle, textAlign: 'center' }}>{plays}</td>
                      <td style={{ ...tdStyle, textAlign: 'center', fontWeight: 700, color: 'var(--color-primary)' }}>
                        {user.winCount}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>

        {/* Game Ranking card */}
        <div className="card" style={chartCardStyle}>
          <h3 style={{ ...chartTitleStyle, display: 'flex', alignItems: 'center', gap: '6px' }}>
            <TrophyIcon size={16} style={{ color: 'var(--color-accent)' }} />
            <span>Ranking por Jogos</span>
          </h3>
          <p style={chartSubTitleStyle}>Os jogos com mais partidas registradas e maior frequência de vitórias.</p>

          <div style={rankingTableContainerStyle}>
            <table style={rankingTableStyle}>
              <thead>
                <tr style={tableHeaderStyle}>
                  <th style={thStyle}>Pos.</th>
                  <th style={thStyle}>Jogo</th>
                  <th style={{ ...thStyle, textAlign: 'center' }}>Partidas</th>
                  <th style={{ ...thStyle, textAlign: 'center' }}>Vitórias</th>
                </tr>
              </thead>
              <tbody>
                {gameRankings.slice(0, 5).map((item, idx) => (
                  <tr key={item.game.id} style={tableRowStyle}>
                    <td style={{ ...tdStyle, fontWeight: 700 }}>
                      {idx === 0 ? '1º' : idx === 1 ? '2º' : idx === 2 ? '3º' : `${idx + 1}`}
                    </td>
                    <td style={{ ...tdStyle, fontWeight: 600 }}>{item.game.name}</td>
                    <td style={{ ...tdStyle, textAlign: 'center' }}>{item.plays}</td>
                    <td style={{ ...tdStyle, textAlign: 'center', fontWeight: 700, color: 'var(--color-primary)' }}>{item.wins}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        {/* Advanced Ranking tab/card */}
        <div className="card" style={chartCardStyle}>
          <h3 style={{ ...chartTitleStyle, display: 'flex', alignItems: 'center', gap: '6px' }}>
            <TrophyIcon size={16} style={{ color: 'var(--color-primary)' }} />
            <span>Ranking Avançado</span>
          </h3>
          <p style={chartSubTitleStyle}>Filtre por jogo e veja quem lidera em vitórias nessa categoria.</p>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', marginTop: '14px' }}>
            <label style={{ fontSize: '0.85rem', fontWeight: 600, color: 'var(--color-text-muted)' }}>Filtrar por jogo</label>
            <select
              value={selectedGameFilter}
              onChange={e => setSelectedGameFilter(e.target.value)}
              style={filterSelectStyle}
            >
              {gameFilterOptions.map(option => (
                <option key={option.id} value={option.id}>{option.name}</option>
              ))}
            </select>

            <div style={advancedRankSummaryStyle}>
              <span style={advancedRankLabelStyle}>Quem mais vence</span>
              <strong style={advancedRankValueStyle}>
                {topWinner ? `${topWinner.user.name} • ${topWinner.wins} vitórias` : 'Nenhum dado'}
              </strong>
              <span style={advancedRankHintStyle}>
                {selectedGameFilter === 'all'
                  ? 'Visão geral de todas as partidas registradas.'
                  : `Mostrando o desempenho em ${state.boardGames.find(game => game.id === selectedGameFilter)?.name || 'este jogo'}.`}
              </span>
            </div>

            <div style={rankingTableContainerStyle}>
              <table style={rankingTableStyle}>
                <thead>
                  <tr style={tableHeaderStyle}>
                    <th style={thStyle}>Pos.</th>
                    <th style={thStyle}>Jogador</th>
                    <th style={{ ...thStyle, textAlign: 'center' }}>Vitórias</th>
                    <th style={{ ...thStyle, textAlign: 'center' }}>Partidas</th>
                  </tr>
                </thead>
                <tbody>
                  {advancedRanking.slice(0, 6).map((item, idx) => (
                    <tr key={item.user.id} style={tableRowStyle}>
                      <td style={{ ...tdStyle, fontWeight: 700 }}>{idx + 1}º</td>
                      <td style={{ ...tdStyle, fontWeight: 600 }}>{item.user.name}</td>
                      <td style={{ ...tdStyle, textAlign: 'center', fontWeight: 700, color: 'var(--color-primary)' }}>{item.wins}</td>
                      <td style={{ ...tdStyle, textAlign: 'center' }}>{item.plays}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>

      </div>

      {/* Printable page tables (invisible in browser, rendered when printing PDF) */}
      <div className="print-table-wrapper" style={{ display: 'none' }}>
        <h3 style={{ fontSize: '14pt', margin: '20px 0 10px 0' }}>Ranking Completo dos Membros</h3>
        <table className="print-table">
          <thead>
            <tr>
              <th>Pos.</th>
              <th>Jogador</th>
              <th>Partidas Jogadas</th>
              <th>Vitórias Gravadas</th>
              <th>Aproveitamento (%)</th>
            </tr>
          </thead>
          <tbody>
            {playerRankings.map((user, idx) => {
              const plays = state.sessions.filter(s => s.participantIds.includes(user.id)).length;
              const winRate = plays > 0 ? Math.round((user.winCount / plays) * 100) : 0;
              return (
                <tr key={user.id}>
                  <td>{idx + 1}</td>
                  <td>{user.name}</td>
                  <td>{plays}</td>
                  <td>{user.winCount}</td>
                  <td>{winRate}%</td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

    </div>
  );
};

// Styling structures for Stats Page
const headerSectionStyle: React.CSSProperties = {
  marginBottom: '32px',
  borderBottom: '2px solid var(--color-border)',
  paddingBottom: '16px',
};

const adminReportPanelStyle: React.CSSProperties = {
  display: 'flex',
  justifyContent: 'space-between',
  alignItems: 'center',
  padding: '16px 20px',
  backgroundColor: 'var(--color-primary-light)',
  border: '1px solid rgba(224, 106, 71, 0.15)',
  marginBottom: '28px',
  flexWrap: 'wrap',
  gap: '16px',
};

const exportButtonsWrapperStyle: React.CSSProperties = {
  display: 'flex',
  gap: '12px',
};

const statsBannerGridStyle: React.CSSProperties = {
  display: 'grid',
  gridTemplateColumns: 'repeat(3, 1fr)',
  gap: '20px',
  marginBottom: '32px',
};

const statBannerCardStyle: React.CSSProperties = {
  padding: '16px 20px',
  display: 'flex',
  flexDirection: 'column',
  gap: '4px',
};

const statBannerTitleStyle: React.CSSProperties = {
  fontSize: '0.75rem',
  textTransform: 'uppercase',
  fontWeight: 700,
  letterSpacing: '0.05em',
  color: 'var(--color-text-light)',
};

const statBannerValueStyle: React.CSSProperties = {
  fontSize: '1.6rem',
  fontWeight: 800,
  color: 'var(--color-text-main)',
  fontFamily: 'var(--font-title)',
};

const chartsGridStyle: React.CSSProperties = {
  display: 'grid',
  gridTemplateColumns: 'repeat(2, 1fr)',
  gap: '24px',
};

const chartCardStyle: React.CSSProperties = {
  padding: '24px',
  display: 'flex',
  flexDirection: 'column',
};

const chartTitleStyle: React.CSSProperties = {
  fontSize: '1.1rem',
  fontWeight: 700,
  fontFamily: 'var(--font-title)',
};

const chartSubTitleStyle: React.CSSProperties = {
  fontSize: '0.8rem',
  color: 'var(--color-text-muted)',
  marginTop: '2px',
};

// Donut layout
const donutFlexWrapperStyle: React.CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'space-around',
  gap: '20px',
  marginTop: '20px',
  flexGrow: 1,
};

const donutLegendContainerStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: '10px',
};

const legendRowStyle: React.CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  gap: '8px',
};

const colorIndicatorStyle: React.CSSProperties = {
  width: '12px',
  height: '12px',
  borderRadius: '3px',
  flexShrink: 0,
};

const legendLabelTextStyle: React.CSSProperties = {
  fontSize: '0.8rem',
  color: 'var(--color-text-muted)',
};

// Ranking Table layouts
const rankingTableContainerStyle: React.CSSProperties = {
  marginTop: '16px',
  flexGrow: 1,
};

const filterSelectStyle: React.CSSProperties = {
  width: '100%',
  border: '1px solid var(--color-border)',
  borderRadius: '10px',
  padding: '10px 12px',
  backgroundColor: 'white',
  color: 'var(--color-text-main)',
  fontSize: '0.9rem',
};

const advancedRankSummaryStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: '4px',
  padding: '12px',
  borderRadius: '12px',
  backgroundColor: 'var(--color-primary-light)',
  border: '1px solid rgba(224, 106, 71, 0.15)',
};

const advancedRankLabelStyle: React.CSSProperties = {
  fontSize: '0.75rem',
  textTransform: 'uppercase',
  letterSpacing: '0.08em',
  color: 'var(--color-text-muted)',
};

const advancedRankValueStyle: React.CSSProperties = {
  fontSize: '1rem',
  color: 'var(--color-text-main)',
  fontWeight: 800,
};

const advancedRankHintStyle: React.CSSProperties = {
  fontSize: '0.82rem',
  color: 'var(--color-text-muted)',
};

const rankingTableStyle: React.CSSProperties = {
  width: '100%',
  borderCollapse: 'collapse',
  fontSize: '0.85rem',
};

const tableHeaderStyle: React.CSSProperties = {
  borderBottom: '2px solid var(--color-border)',
  color: 'var(--color-text-muted)',
};

const thStyle: React.CSSProperties = {
  padding: '8px',
  fontWeight: 700,
  textAlign: 'left',
};

const tableRowStyle: React.CSSProperties = {
  borderBottom: '1px solid #FAF9F6',
};

const tdStyle: React.CSSProperties = {
  padding: '10px 8px',
};

// Inject CSS styles for Print-Only layout toggles
const responsiveStatsStyle = `
@media (max-width: 900px) {
  .charts-grid-responsive {
    grid-template-columns: 1fr !important;
  }
  .stats-banner-responsive {
    grid-template-columns: 1fr !important;
    gap: 12px !important;
  }
}
@media print {
  .print-table-wrapper {
    display: block !important;
  }
  .charts-grid-responsive {
    grid-template-columns: 1.2fr 0.8fr !important;
    gap: 30px !important;
  }
}
`;
if (typeof document !== 'undefined') {
  const styleEl = document.createElement('style');
  styleEl.textContent = responsiveStatsStyle;
  document.head.appendChild(styleEl);
}
export default Stats;
