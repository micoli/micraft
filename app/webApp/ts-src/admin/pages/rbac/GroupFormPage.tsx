import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router";
import { getApiAdminGroups, postApiAdminGroups, putApiAdminGroupsByName } from "../../../generated/api/requests";
import { GroupDto } from "../../apiTypes";
import { useT } from "../../i18n";
import { GroupForm } from "./GroupForm";

/** Full-page create/edit form — `/admin/rbac/new` (isNew) or `/admin/rbac/:name` (edit). */
export function GroupFormPage() {
  const t = useT();
  const navigate = useNavigate();
  const { name } = useParams();
  const isNew = name === undefined;
  const [group, setGroup] = useState<GroupDto | null>(null);
  const [loading, setLoading] = useState(!isNew);
  const [notFound, setNotFound] = useState(false);

  useEffect(() => {
    if (isNew) return;
    void getApiAdminGroups().then(({ data }) => {
      const found = data?.groups.find((g) => g.name === name);
      if (found) setGroup(found);
      else setNotFound(true);
      setLoading(false);
    });
  }, [isNew, name]);

  const back = () => navigate("/admin/rbac");

  const handleSave = async (g: { name: string; permissions: string[] }) => {
    if (isNew) {
      const { response } = await postApiAdminGroups({ body: g });
      if (!response?.ok) throw new Error(t("common.serverError", response?.status ?? 0));
    } else {
      const { response } = await putApiAdminGroupsByName({
        path: { name: name! },
        body: { permissions: g.permissions },
      });
      if (!response?.ok) throw new Error(t("common.serverError", response?.status ?? 0));
    }
    back();
  };

  return (
    <div className="max-w-2xl space-y-5">
      <Link to="/admin/rbac" className="text-sm text-[#818CF8] hover:text-white">
        {t("rbac.backToList")}
      </Link>
      <h1 className="text-lg font-semibold text-white">{isNew ? t("rbac.addTitle") : t("rbac.editTitle")}</h1>

      {loading ? (
        <p className="text-[#8A99AF] text-sm animate-pulse">{t("common.loading")}</p>
      ) : notFound ? (
        <p className="text-[#8A99AF] text-sm">{t("rbac.notFound")}</p>
      ) : (
        <GroupForm
          initial={isNew ? { permissions: [] } : (group ?? {})}
          isNew={isNew}
          onSave={handleSave}
          onClose={back}
        />
      )}
    </div>
  );
}
