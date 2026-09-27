interface Props {
  title: string;
  ids: string[];
  onNavigate: (id: string) => void;
}

export function Dependencies({ title, ids, onNavigate }: Props) {
  if (ids.length === 0) return null;

  return (
    <div className="dependencies">
      <b>{title}:</b>
      {ids.map((id) => (
        <button key={id} onClick={() => onNavigate(id)}>
          {id.split("/").pop()}
        </button>
      ))}
    </div>
  );
}
