import { serve } from "https://deno.land/std@0.224.0/http/server.ts";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

type NotificationPayload = {
  user_id?: string;
  user_email?: string;
  title?: string;
  message?: string;
  type?: string;
  target_role?: string;
  related_id?: string;
  metadata?: string;
};

serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const payload = (await req.json()) as NotificationPayload;
    const resendApiKey = Deno.env.get("RESEND_API_KEY") ?? "";
    const fromEmail = Deno.env.get("NOTIFICATION_FROM_EMAIL") ?? "";
    const toEmail = await resolveRecipientEmail(payload);

    if (!resendApiKey || !fromEmail || !toEmail) {
      return json({ sent: false, skipped: true });
    }

    const response = await fetch("https://api.resend.com/emails", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${resendApiKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        from: fromEmail,
        to: [toEmail],
        subject: payload.title || "HotelGo notification",
        text: payload.message || "",
      }),
    });

    if (!response.ok) {
      const body = await response.text();
      return json({ sent: false, error: body }, 502);
    }

    return json({ sent: true });
  } catch (error) {
    return json({ sent: false, error: String(error) }, 500);
  }
});

async function resolveRecipientEmail(payload: NotificationPayload): Promise<string> {
  if (payload.user_email) return payload.user_email;
  if (!payload.user_id) return "";

  const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
  if (!supabaseUrl || !serviceRoleKey) return "";

  const response = await fetch(`${supabaseUrl}/rest/v1/users?id=eq.${encodeURIComponent(payload.user_id)}&select=email&limit=1`, {
    headers: {
      apikey: serviceRoleKey,
      Authorization: `Bearer ${serviceRoleKey}`,
    },
  });
  if (!response.ok) return "";

  const rows = await response.json() as Array<{ email?: string }>;
  return rows[0]?.email ?? "";
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}
