import { UserDto } from "../../api";
import { useState } from "react";
import { useT } from "../../i18n";
import { useForm } from "@tanstack/react-form";
import { Field } from "./Field";
import { TextInput } from "./TextInput";
import { Button } from "../../../primitives/Button";

export function UserForm({
  initial,
  onSave,
  onClose,
  isNew,
  noauth = false,
}: {
  initial: Partial<UserDto & { password: string }>;
  onSave: (u: UserDto & { password?: string }) => Promise<void>;
  onClose: () => void;
  isNew: boolean;
  noauth?: boolean;
}) {
  const t = useT();
  const [error, setError] = useState<string | null>(null);

  const form = useForm({
    defaultValues: {
      email: initial.email ?? "",
      password: "",
      displayName: initial.displayName ?? "",
      groups: (initial.groups ?? []).join(", "),
    },
    onSubmit: async ({ value }) => {
      setError(null);
      try {
        await onSave({
          email: value.email,
          displayName: value.displayName || value.email,
          groups: value.groups
            .split(",")
            .map((g) => g.trim())
            .filter(Boolean),
          password: isNew ? value.password : undefined,
        });
        onClose();
      } catch (e: unknown) {
        setError(e instanceof Error ? e.message : t("common.error"));
      }
    },
  });

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        e.stopPropagation();
        form.handleSubmit();
      }}
      className="space-y-4"
    >
      {isNew && (
        <Field label={t("users.email")}>
          <form.Field name="email">
            {(field) => (
              <TextInput
                value={field.state.value}
                onChange={field.handleChange}
                onBlur={field.handleBlur}
                placeholder={t("users.emailPlaceholder")}
              />
            )}
          </form.Field>
        </Field>
      )}
      {isNew && !noauth && (
        <Field label={t("users.password")}>
          <form.Field name="password">
            {(field) => (
              <TextInput
                value={field.state.value}
                onChange={field.handleChange}
                onBlur={field.handleBlur}
                type="password"
              />
            )}
          </form.Field>
        </Field>
      )}
      {!noauth && (
        <Field label={t("users.displayName")}>
          <form.Field name="displayName">
            {(field) => (
              <form.Subscribe selector={(state) => state.values.email}>
                {(email) => (
                  <TextInput
                    value={field.state.value}
                    onChange={field.handleChange}
                    onBlur={field.handleBlur}
                    placeholder={email}
                  />
                )}
              </form.Subscribe>
            )}
          </form.Field>
        </Field>
      )}
      {!noauth && (
        <Field label={t("users.groupsField")}>
          <form.Field name="groups">
            {(field) => (
              <TextInput
                value={field.state.value}
                onChange={field.handleChange}
                onBlur={field.handleBlur}
                placeholder={t("users.groupsPlaceholder")}
              />
            )}
          </form.Field>
        </Field>
      )}
      {error && <p className="text-red-400 text-xs">{error}</p>}
      <div className="flex gap-2 justify-end pt-1">
        <Button variant="ghost" onClick={onClose} type="button">
          {t("common.cancel")}
        </Button>
        <form.Subscribe selector={(state) => state.isSubmitting}>
          {(isSubmitting) => (
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? t("common.saving") : t("common.save")}
            </Button>
          )}
        </form.Subscribe>
      </div>
    </form>
  );
}
