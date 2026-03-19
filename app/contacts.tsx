/**
 * Emergency Contacts Management Screen
 * Add, edit, and manage emergency contacts for the inner circle
 */

import { router } from 'expo-router';
import { useCallback, useEffect, useState } from 'react';
import {
    Alert,
    Linking,
    Platform,
    ScrollView,
    StyleSheet,
    TouchableOpacity,
    View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { AppModal } from '@/components/AppModal';
import { AppTextInput } from '@/components/AppTextInput';
import { PrimaryButton } from '@/components/PrimaryButton';
import { ScreenHeader } from '@/components/ScreenHeader';
import { SecondaryButton } from '@/components/SecondaryButton';
import { ThemedText } from '@/components/ThemedText';
import { ThemedView } from '@/components/ThemedView';
import { IconSymbol } from '@/components/ui/IconSymbol';
import { Colors, semantic } from '@/constants/Colors';
import { Spacing, Radius } from '@/constants/Spacing';
import { Typography } from '@/constants/Typography';


import { useTheme } from '@/context/ThemeContext';
import {
    Contact,
    createContact,
    deleteContact,
    getContacts,
    updateContact,
} from '@/database/contacts';
import { formatPhoneInput, formatPhoneNumber } from '@/utils/phone';

type ContactRole = 'primary_caregiver' | 'caregiver' | 'neighbor' | 'family' | 'friend' | 'other';

const ROLE_OPTIONS: { value: ContactRole; label: string; icon: string }[] = [
  { value: 'primary_caregiver', label: 'Primary Caregiver', icon: 'star.fill' },
  { value: 'caregiver', label: 'Caregiver', icon: 'heart.fill' },
  { value: 'family', label: 'Family', icon: 'person.2.fill' },
  { value: 'neighbor', label: 'Neighbor', icon: 'house.fill' },
  { value: 'friend', label: 'Friend', icon: 'person.fill' },
  { value: 'other', label: 'Other', icon: 'ellipsis' },
];

interface FormData {
  name: string;
  phone: string;
  relationship: string;
  role: ContactRole;
  address: string;
  notifyOnEmergency: boolean;
  shareMedicalInfo: boolean;
  notes: string;
}

const EMPTY_FORM: FormData = {
  name: '',
  phone: '',
  relationship: '',
  role: 'family',
  address: '',
  notifyOnEmergency: true,
  shareMedicalInfo: false,
  notes: '',
};

export default function ContactsScreen() {
  const { colorScheme } = useTheme();
  const theme = Colors[colorScheme];

  const [contacts, setContacts] = useState<Contact[]>([]);

  // Modal state
  const [modalVisible, setModalVisible] = useState(false);
  const [modalType, setModalType] = useState<'delete' | 'error' | 'validation' | null>(null);
  const [modalMessage, setModalMessage] = useState('');
  const [pendingDelete, setPendingDelete] = useState<Contact | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [editingContact, setEditingContact] = useState<Contact | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [formData, setFormData] = useState<FormData>(EMPTY_FORM);

  const loadContacts = useCallback(async () => {
    try {
      const data = await getContacts();
      setContacts(data);
    } catch (error) {
      console.error('Failed to load contacts:', error);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    loadContacts();
  }, [loadContacts]);

  const showAlert = (type: 'error' | 'validation', message: string) => {
    if (Platform.OS === 'web') {
      setModalType(type);
      setModalMessage(message);
      setModalVisible(true);
    } else {
      Alert.alert(type === 'validation' ? 'Required' : 'Error', message);
    }
  };

  const handleSave = async () => {
    if (!formData.name.trim()) {
      showAlert('validation', 'Please enter a name for this contact.');
      return;
    }
    if (!formData.phone.trim()) {
      showAlert('validation', 'Please enter a phone number.');
      return;
    }

    try {
      if (editingContact?.id) {
        await updateContact(editingContact.id, {
          name: formData.name,
          phone: formData.phone,
          relationship: formData.relationship || undefined,
          role: formData.role,
          address: formData.address || undefined,
          notifyOnEmergency: formData.notifyOnEmergency,
          shareMedicalInfo: formData.shareMedicalInfo,
          notes: formData.notes || undefined,
        });
      } else {
        await createContact({
          name: formData.name,
          phone: formData.phone,
          relationship: formData.relationship || undefined,
          role: formData.role,
          address: formData.address || undefined,
          notifyOnEmergency: formData.notifyOnEmergency,
          shareMedicalInfo: formData.shareMedicalInfo,
          notes: formData.notes || undefined,
        });
      }

      await loadContacts();
      setShowForm(false);
      setEditingContact(null);
      setFormData(EMPTY_FORM);
    } catch (error) {
      console.error('Failed to save contact:', error);
      showAlert('error', 'Failed to save contact. Please try again.');
    }
  };

  const handleEdit = (contact: Contact) => {
    setEditingContact(contact);
    setFormData({
      name: contact.name,
      phone: contact.phone,
      relationship: contact.relationship || '',
      role: contact.role || 'other',
      address: contact.address || '',
      notifyOnEmergency: contact.notifyOnEmergency,
      shareMedicalInfo: contact.shareMedicalInfo,
      notes: contact.notes || '',
    });
    setShowForm(true);
  };

  const handleDelete = (contact: Contact) => {
    if (Platform.OS === 'web') {
      setPendingDelete(contact);
      setModalType('delete');
      setModalMessage(`Are you sure you want to remove ${contact.name}?`);
      setModalVisible(true);
    } else {
      Alert.alert('Delete Contact', `Are you sure you want to remove ${contact.name}?`, [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Delete',
          style: 'destructive',
          onPress: () => confirmDelete(contact),
        },
      ]);
    }
  };

  const confirmDelete = async (contact: Contact) => {
    try {
      if (contact.id) {
        await deleteContact(contact.id);
        await loadContacts();
      }
    } catch (error) {
      console.error('Failed to delete contact:', error);
      showAlert('error', 'Failed to delete contact.');
    }
  };

  const handleModalAction = (action: 'confirm' | 'cancel') => {
    setModalVisible(false);
    if (action === 'confirm' && modalType === 'delete' && pendingDelete) {
      confirmDelete(pendingDelete);
    }
    setPendingDelete(null);
    setModalType(null);
    setModalMessage('');
  };

  const handleCall = (phone: string) => {
    Linking.openURL(`tel:${phone}`);
  };

  const handleAddNew = () => {
    setEditingContact(null);
    setFormData(EMPTY_FORM);
    setShowForm(true);
  };

  const handleCancel = () => {
    setShowForm(false);
    setEditingContact(null);
    setFormData(EMPTY_FORM);
  };

  const getRoleInfo = (role?: ContactRole) => {
    return ROLE_OPTIONS.find((r) => r.value === role) || ROLE_OPTIONS[5];
  };

  const renderContactCard = (contact: Contact) => {
    const roleInfo = getRoleInfo(contact.role);

    return (
      <View
        key={contact.id}
        style={[
          styles.contactCard,
          {
            backgroundColor: theme.card,
            borderColor: theme.border,
          },
        ]}
      >
        <View style={styles.contactHeader}>
          <View style={styles.contactInfo}>
            <ThemedText style={styles.contactName}>{contact.name}</ThemedText>
            <View style={styles.roleRow}>
              <IconSymbol
                name={roleInfo.icon as any}
                size={12}
                color={theme.icon}
              />
              <ThemedText style={[styles.contactRole, { color: theme.textSecondary }]}>
                {roleInfo.label}
              </ThemedText>
            </View>
          </View>

          <View style={styles.contactActions}>
            <TouchableOpacity
              style={[styles.actionButton, { backgroundColor: semantic.success }]}
              onPress={() => handleCall(contact.phone)}
            >
              <IconSymbol name="phone.fill" size={16} color="#fff" />
            </TouchableOpacity>
            <TouchableOpacity
              style={[styles.actionButton, { backgroundColor: theme.primary }]}
              onPress={() => handleEdit(contact)}
            >
              <IconSymbol name="pencil" size={16} color="#fff" />
            </TouchableOpacity>
          </View>
        </View>

        <View style={styles.contactDetails}>
          <View style={styles.detailRow}>
            <IconSymbol name="phone" size={14} color={theme.icon} />
            <ThemedText style={[styles.detailText, { color: theme.text }]}>
              {formatPhoneNumber(contact.phone)}
            </ThemedText>
          </View>

          {contact.relationship && (
            <View style={styles.detailRow}>
              <IconSymbol name="person" size={14} color={theme.icon} />
              <ThemedText style={[styles.detailText, { color: theme.text }]}>
                {contact.relationship}
              </ThemedText>
            </View>
          )}

          {contact.address && (
            <View style={styles.detailRow}>
              <IconSymbol name="location" size={14} color={theme.icon} />
              <ThemedText
                style={[styles.detailText, { color: theme.text }]}
                numberOfLines={2}
              >
                {contact.address}
              </ThemedText>
            </View>
          )}
        </View>

        <TouchableOpacity style={styles.deleteButton} onPress={() => handleDelete(contact)}>
          <ThemedText style={[styles.deleteText, { color: semantic.error }]}>Remove</ThemedText>
        </TouchableOpacity>
      </View>
    );
  };

  const renderForm = () => (
    <ScrollView style={styles.formContainer} showsVerticalScrollIndicator={false}>
      <ThemedText style={styles.formTitle}>
        {editingContact ? 'Edit Contact' : 'Add Emergency Contact'}
      </ThemedText>

      {/* Name */}
      <AppTextInput
        label="Name"
        value={formData.name}
        onChangeText={(text) => setFormData({ ...formData, name: text })}
        placeholder="Contact name"
        required
      />

      {/* Phone */}
      <AppTextInput
        label="Phone Number"
        value={formData.phone}
        onChangeText={(text) => setFormData({ ...formData, phone: formatPhoneInput(text) })}
        placeholder="(555) 123-4567"
        keyboardType="phone-pad"
        autoComplete="tel"
        required
      />

      {/* Relationship */}
      <AppTextInput
        label="Relationship"
        value={formData.relationship}
        onChangeText={(text) => setFormData({ ...formData, relationship: text })}
        placeholder="e.g., Daughter, Next-door neighbor"
      />

      {/* Address */}
      <AppTextInput
        label="Address"
        value={formData.address}
        onChangeText={(text) => setFormData({ ...formData, address: text })}
        placeholder="Street address, city, state"
        multiline
        numberOfLines={2}
      />

      {/* Role */}
      <View style={styles.formField}>
        <ThemedText style={[styles.fieldLabel, { color: theme.text }]}>
          Role
        </ThemedText>
        <View style={styles.roleGrid}>
          {ROLE_OPTIONS.map((option) => {
            const isSelected = formData.role === option.value;
            return (
              <TouchableOpacity
                key={option.value}
                style={[
                  styles.roleOption,
                  {
                    backgroundColor: isSelected ? theme.primary : theme.inputBackground,
                    borderColor: isSelected ? theme.primary : theme.inputBorder,
                  },
                ]}
                onPress={() => setFormData({ ...formData, role: option.value })}
              >
                <IconSymbol
                  name={option.icon as any}
                  size={14}
                  color={isSelected ? '#fff' : theme.icon}
                />
                <ThemedText
                  style={[
                    styles.roleOptionText,
                    isSelected ? { color: '#fff' } : { color: theme.text },
                  ]}
                >
                  {option.label}
                </ThemedText>
              </TouchableOpacity>
            );
          })}
        </View>
      </View>

      {/* Notify on Emergency */}
      <TouchableOpacity
        style={[
          styles.toggleField,
          {
            backgroundColor: theme.inputBackground,
            borderColor: theme.inputBorder,
          },
        ]}
        onPress={() => setFormData({ ...formData, notifyOnEmergency: !formData.notifyOnEmergency })}
      >
        <View style={styles.toggleInfo}>
          <ThemedText style={styles.toggleLabel}>Notify in Emergency</ThemedText>
          <ThemedText style={[styles.toggleHint, { color: theme.textSecondary }]}>
            Include in emergency SMS alerts
          </ThemedText>
        </View>
        <View
          style={[
            styles.toggle,
            {
              backgroundColor: formData.notifyOnEmergency ? semantic.success : theme.border,
            },
          ]}
        >
          <View
            style={[styles.toggleKnob, formData.notifyOnEmergency && styles.toggleKnobActive]}
          />
        </View>
      </TouchableOpacity>

      {/* Share Medical Info */}
      <TouchableOpacity
        style={[
          styles.toggleField,
          {
            backgroundColor: theme.inputBackground,
            borderColor: theme.inputBorder,
          },
        ]}
        onPress={() => setFormData({ ...formData, shareMedicalInfo: !formData.shareMedicalInfo })}
      >
        <View style={styles.toggleInfo}>
          <ThemedText style={styles.toggleLabel}>Share Medical Info</ThemedText>
          <ThemedText style={[styles.toggleHint, { color: theme.textSecondary }]}>
            Include medical details when alerting
          </ThemedText>
        </View>
        <View
          style={[
            styles.toggle,
            {
              backgroundColor: formData.shareMedicalInfo ? semantic.success : theme.border,
            },
          ]}
        >
          <View style={[styles.toggleKnob, formData.shareMedicalInfo && styles.toggleKnobActive]} />
        </View>
      </TouchableOpacity>

      {/* Notes */}
      <AppTextInput
        label="Notes"
        value={formData.notes}
        onChangeText={(text) => setFormData({ ...formData, notes: text })}
        placeholder="Additional notes about this contact..."
        multiline
        numberOfLines={3}
      />

      {/* Buttons */}
      <View style={styles.formButtons}>
        <SecondaryButton label="Cancel" onPress={handleCancel} style={{ flex: 1 }} />
        <PrimaryButton
          label={editingContact ? 'Update' : 'Add'}
          onPress={handleSave}
          style={{ flex: 1 }}
        />
      </View>

      <View style={{ height: 40 }} />
    </ScrollView>
  );

  const renderContactList = () => (
    <ScrollView style={styles.listContainer} showsVerticalScrollIndicator={false}>
      {contacts.length === 0 ? (
        <View style={styles.emptyState}>
          <IconSymbol name="person.2" size={48} color={theme.icon} />
          <ThemedText style={[styles.emptyTitle, { color: theme.text }]}>
            No Emergency Contacts
          </ThemedText>
          <ThemedText style={[styles.emptyText, { color: theme.textSecondary }]}>
            Add family members, neighbors, or friends who can help in an emergency.
          </ThemedText>
        </View>
      ) : (
        <>
          <View style={styles.listHeader}>
            <ThemedText style={[styles.listCount, { color: theme.textSecondary }]}>
              {contacts.length} contact{contacts.length !== 1 ? 's' : ''}
            </ThemedText>
          </View>
          {contacts.map(renderContactCard)}
        </>
      )}

      <TouchableOpacity
        style={[styles.addButton, { backgroundColor: theme.primary }]}
        onPress={handleAddNew}
      >
        <IconSymbol name="plus" size={20} color="#fff" />
        <ThemedText style={styles.addButtonText}>Add Contact</ThemedText>
      </TouchableOpacity>

      <View style={{ height: 40 }} />
    </ScrollView>
  );

  return (
    <ThemedView style={styles.container}>
      <SafeAreaView style={styles.safeArea} edges={['top']}>
        {/* Header */}
        <ScreenHeader title="Emergency Contacts" />

        {/* Content */}
        {isLoading ? (
          <View style={styles.loading}>
            <ThemedText>Loading...</ThemedText>
          </View>
        ) : showForm ? (
          renderForm()
        ) : (
          renderContactList()
        )}
      </SafeAreaView>

      <AppModal
        visible={modalVisible}
        onDismiss={() => handleModalAction('cancel')}
        title={
          modalType === 'delete' ? 'Delete Contact' : modalType === 'validation' ? 'Required' : 'Error'
        }
        message={modalMessage}
        type={modalType === 'delete' ? 'delete' : 'alert'}
        onConfirm={() => handleModalAction('confirm')}
      />
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  safeArea: {
    flex: 1,
  },
  loading: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },

  // List styles
  listContainer: {
    flex: 1,
    padding: Spacing.lg,
  },
  listHeader: {
    marginBottom: Spacing.md,
  },
  listCount: {
    fontSize: 14,
  },
  emptyState: {
    alignItems: 'center',
    paddingVertical: 60,
    paddingHorizontal: Spacing.xxl,
  },
  emptyTitle: {
    fontSize: 18,
    fontWeight: '600',
    marginTop: Spacing.lg,
    marginBottom: Spacing.sm,
  },
  emptyText: {
    fontSize: 14,
    textAlign: 'center',
    lineHeight: 20,
  },

  // Contact card
  contactCard: {
    borderRadius: Radius.lg,
    borderWidth: 1,
    padding: Spacing.lg,
    marginBottom: Spacing.md,
  },
  contactHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: Spacing.md,
  },
  contactInfo: {
    flex: 1,
  },
  contactName: {
    fontSize: 17,
    fontWeight: '600',
  },
  roleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.xs,
    marginTop: 2,
  },
  contactRole: {
    ...Typography.caption,
  },
  contactActions: {
    flexDirection: 'row',
    gap: Spacing.sm,
  },
  actionButton: {
    width: 36,
    height: 36,
    borderRadius: 18,
    justifyContent: 'center',
    alignItems: 'center',
  },
  contactDetails: {
    gap: 6,
  },
  detailRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.sm,
  },
  detailText: {
    fontSize: 14,
    flex: 1,
  },
  deleteButton: {
    marginTop: Spacing.md,
    paddingTop: Spacing.md,
    borderTopWidth: 1,
    borderTopColor: 'rgba(128,128,128,0.2)',
    alignItems: 'center',
  },
  deleteText: {
    fontSize: 14,
    fontWeight: '500',
  },
  addButton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: Spacing.sm,
    padding: Spacing.lg,
    borderRadius: Radius.lg,
    marginTop: Spacing.sm,
  },
  addButtonText: {
    color: '#fff',
    ...Typography.bodyBold,
  },

  // Form styles
  formContainer: {
    flex: 1,
    padding: Spacing.lg,
  },
  formTitle: {
    fontSize: 22,
    fontWeight: '700',
    marginBottom: 20,
  },
  formField: {
    marginBottom: Spacing.lg,
  },
  fieldLabel: {
    fontSize: 14,
    fontWeight: '500',
    marginBottom: 6,
  },
  roleGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.sm,
  },
  roleOption: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.sm,
    borderRadius: Radius.md,
    borderWidth: 1,
  },
  roleOptionText: {
    fontSize: 13,
    fontWeight: '500',
  },
  toggleField: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: 14,
    borderRadius: 10,
    borderWidth: 1,
    marginBottom: Spacing.lg,
  },
  toggleInfo: {
    flex: 1,
    marginRight: Spacing.md,
  },
  toggleLabel: {
    fontSize: 15,
    fontWeight: '500',
  },
  toggleHint: {
    fontSize: 12,
    marginTop: 2,
  },
  toggle: {
    width: 50,
    height: 30,
    borderRadius: 15,
    padding: 3,
  },
  toggleKnob: {
    width: 24,
    height: 24,
    borderRadius: 12,
    backgroundColor: '#fff',
  },
  toggleKnobActive: {
    transform: [{ translateX: 20 }],
  },
  formButtons: {
    flexDirection: 'row',
    gap: Spacing.md,
    marginTop: Spacing.xl,
  },
});
