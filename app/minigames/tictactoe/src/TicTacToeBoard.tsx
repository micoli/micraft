import { Board } from "./ticTacToeLogic";
import { TicTacToeCell } from "./TicTacToeCell";

interface Props {
  board: Board;
  disabled: boolean;
  onPlay: (cellIndex: number) => void;
}

export function TicTacToeBoard({ board, disabled, onPlay }: Props) {
  return (
    <div style={{ display: "grid", gridTemplateColumns: "repeat(3, 72px)", gap: 4 }}>
      {board.map((value, i) => (
        <TicTacToeCell key={i} value={value} disabled={disabled} onClick={() => onPlay(i)} />
      ))}
    </div>
  );
}
