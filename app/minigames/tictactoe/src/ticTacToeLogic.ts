// Pure game rules — no React, no network. Board is a flat 9-cell array, index 0..8
// (row-major, 3x3). Symbol is derived from the room's member order: the first member
// (index 0, always the host at room creation time) plays X, the second plays O.

export type CellValue = "X" | "O" | null;
export type Board = CellValue[];

export const EMPTY_BOARD: Board = Array(9).fill(null);

const WIN_LINES: readonly (readonly [number, number, number])[] = [
  [0, 1, 2],
  [3, 4, 5],
  [6, 7, 8],
  [0, 3, 6],
  [1, 4, 7],
  [2, 5, 8],
  [0, 4, 8],
  [2, 4, 6],
];

export function symbolForMemberIndex(index: number): CellValue {
  if (index === 0) return "X";
  if (index === 1) return "O";
  return null;
}

export function winner(board: Board): CellValue {
  for (const [a, b, c] of WIN_LINES) {
    if (board[a] && board[a] === board[b] && board[a] === board[c]) return board[a];
  }
  return null;
}

export function isDraw(board: Board): boolean {
  return winner(board) === null && board.every((c) => c !== null);
}

export function movesPlayed(board: Board): number {
  return board.filter((c) => c !== null).length;
}

/** Whose turn it is, derived purely from how many cells are filled: X always starts. */
export function currentTurn(board: Board): CellValue {
  return movesPlayed(board) % 2 === 0 ? "X" : "O";
}

export function canPlay(board: Board, cellIndex: number, mySymbol: CellValue): boolean {
  if (winner(board) || isDraw(board)) return false;
  if (cellIndex < 0 || cellIndex > 8) return false;
  if (board[cellIndex] !== null) return false;
  return currentTurn(board) === mySymbol;
}

export function playMove(board: Board, cellIndex: number, symbol: CellValue): Board {
  const next = [...board];
  next[cellIndex] = symbol;
  return next;
}
